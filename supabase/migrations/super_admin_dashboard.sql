-- ============================================================================
-- VYBAL — SUPER ADMIN DASHBOARD & SERVER-SIDE SECURITY MIGRATION
-- Non-destructive, idempotent SQL migration.
-- Run in the Supabase SQL Editor after reviewing.
-- Preserves existing tables (profiles, posts, notifications, messages)
-- and existing storage bucket ("posts").
-- ============================================================================

-- 1. Extend existing `profiles` table non-destructively
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS email text,
    ADD COLUMN IF NOT EXISTS role text NOT NULL DEFAULT 'user',
    ADD COLUMN IF NOT EXISTS is_verified boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS verification_tier text,
    ADD COLUMN IF NOT EXISTS account_status text NOT NULL DEFAULT 'active',
    ADD COLUMN IF NOT EXISTS restriction_reason text,
    ADD COLUMN IF NOT EXISTS last_seen_at timestamptz DEFAULT now();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'profiles_role_check'
    ) THEN
        ALTER TABLE public.profiles
            ADD CONSTRAINT profiles_role_check
            CHECK (role IN ('user', 'moderator', 'admin', 'super_admin'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'profiles_account_status_check'
    ) THEN
        ALTER TABLE public.profiles
            ADD CONSTRAINT profiles_account_status_check
            CHECK (account_status IN ('active', 'suspended', 'banned'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'profiles_verification_tier_check'
    ) THEN
        ALTER TABLE public.profiles
            ADD CONSTRAINT profiles_verification_tier_check
            CHECK (verification_tier IS NULL OR verification_tier IN ('creator', 'business', 'super_admin'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_profiles_role ON public.profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_status ON public.profiles(account_status);
CREATE INDEX IF NOT EXISTS idx_profiles_last_seen ON public.profiles(last_seen_at DESC);

-- ============================================================================
-- 2. Permanent Super Admin Helpers & Server-Side Auth Exception
-- Permanent Super Admin Gmail: nasirandco001@gmail.com
-- ============================================================================

CREATE OR REPLACE FUNCTION public.vybal_is_permanent_super_admin_email(check_email text)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT lower(trim(coalesce(check_email, ''))) = 'nasirandco001@gmail.com';
$$;

CREATE OR REPLACE FUNCTION public.vybal_caller_role()
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT CASE
        WHEN EXISTS (
            SELECT 1 FROM auth.users u
            WHERE u.id = auth.uid()
              AND public.vybal_is_permanent_super_admin_email(u.email)
        ) THEN 'super_admin'
        ELSE coalesce(
            (SELECT p.role FROM public.profiles p WHERE p.id = auth.uid()),
            'user'
        )
    END;
$$;

CREATE OR REPLACE FUNCTION public.vybal_is_super_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT public.vybal_caller_role() = 'super_admin';
$$;

CREATE OR REPLACE FUNCTION public.vybal_is_staff()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, auth
AS $$
    SELECT public.vybal_caller_role() IN ('super_admin', 'admin', 'moderator');
$$;

-- Server-side exception: auto-confirm ONLY nasirandco001@gmail.com on insert/update
-- Preserves password authentication; never applies to any other email.
CREATE OR REPLACE FUNCTION public.vybal_auto_confirm_super_admin_auth()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    IF public.vybal_is_permanent_super_admin_email(NEW.email) THEN
        IF NEW.email_confirmed_at IS NULL THEN
            NEW.email_confirmed_at := now();
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_vybal_auto_confirm_super_admin_auth ON auth.users;
CREATE TRIGGER trg_vybal_auto_confirm_super_admin_auth
    BEFORE INSERT OR UPDATE OF email, email_confirmed_at ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.vybal_auto_confirm_super_admin_auth();

-- Automatically grant super_admin + verified status in public.profiles
CREATE OR REPLACE FUNCTION public.vybal_sync_super_admin_profile()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    IF public.vybal_is_permanent_super_admin_email(NEW.email) THEN
        INSERT INTO public.profiles (
            id, email, username, display_name, role, is_verified, verification_tier, account_status, last_seen_at
        )
        VALUES (
            NEW.id,
            lower(trim(NEW.email)),
            coalesce(NULLIF(trim(NEW.raw_user_meta_data->>'username'), ''), 'vybal_super_admin'),
            coalesce(NULLIF(trim(NEW.raw_user_meta_data->>'display_name'), ''), 'VYBAL Super Admin'),
            'super_admin',
            true,
            'super_admin',
            'active',
            now()
        )
        ON CONFLICT (id) DO UPDATE SET
            email = lower(trim(NEW.email)),
            role = 'super_admin',
            is_verified = true,
            verification_tier = 'super_admin',
            account_status = 'active';
    ELSE
        -- Sync email onto existing profile if present
        UPDATE public.profiles
        SET email = lower(trim(NEW.email))
        WHERE id = NEW.id AND (email IS NULL OR email = '');
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_vybal_sync_super_admin_profile ON auth.users;
CREATE TRIGGER trg_vybal_sync_super_admin_profile
    AFTER INSERT OR UPDATE OF email ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.vybal_sync_super_admin_profile();

-- Handle an existing unverified Super Admin account immediately and securely
UPDATE auth.users
SET email_confirmed_at = COALESCE(email_confirmed_at, now())
WHERE public.vybal_is_permanent_super_admin_email(email);

INSERT INTO public.profiles (
    id, email, username, display_name, role, is_verified, verification_tier, account_status
)
SELECT
    u.id,
    lower(trim(u.email)),
    'vybal_super_admin',
    'VYBAL Super Admin',
    'super_admin',
    true,
    'super_admin',
    'active'
FROM auth.users u
WHERE public.vybal_is_permanent_super_admin_email(u.email)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    role = 'super_admin',
    is_verified = true,
    verification_tier = 'super_admin',
    account_status = 'active';

-- Protect Super Admin from demotion, unverification, ban, suspension, or deletion,
-- and block any non-designated user from claiming super_admin via email/profile edits.
CREATE OR REPLACE FUNCTION public.vybal_protect_super_admin_profile()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    target_auth_email text;
BEGIN
    IF TG_OP = 'DELETE' THEN
        SELECT email INTO target_auth_email FROM auth.users WHERE id = OLD.id;
        IF OLD.role = 'super_admin' OR public.vybal_is_permanent_super_admin_email(target_auth_email) THEN
            RAISE EXCEPTION 'Protected Super Admin account cannot be deleted.';
        END IF;
        RETURN OLD;
    END IF;

    SELECT email INTO target_auth_email FROM auth.users WHERE id = NEW.id;

    -- Prevent anyone else from claiming super_admin
    IF NEW.role = 'super_admin' AND NOT public.vybal_is_permanent_super_admin_email(target_auth_email) THEN
        RAISE EXCEPTION 'Only the designated permanent Super Admin account may hold super_admin role.';
    END IF;

    -- Prevent demotion, unverification, suspension, or ban of the permanent Super Admin
    IF public.vybal_is_permanent_super_admin_email(target_auth_email) THEN
        NEW.role := 'super_admin';
        NEW.is_verified := true;
        NEW.verification_tier := 'super_admin';
        NEW.account_status := 'active';
        NEW.restriction_reason := NULL;
    END IF;

    -- Prevent non-staff users from self-editing privileged columns
    IF TG_OP = 'UPDATE' AND auth.uid() IS NOT NULL AND NOT public.vybal_is_staff() THEN
        NEW.role := OLD.role;
        NEW.is_verified := OLD.is_verified;
        NEW.verification_tier := OLD.verification_tier;
        NEW.account_status := OLD.account_status;
        NEW.restriction_reason := OLD.restriction_reason;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_vybal_protect_super_admin_profile ON public.profiles;
CREATE TRIGGER trg_vybal_protect_super_admin_profile
    BEFORE INSERT OR UPDATE OR DELETE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.vybal_protect_super_admin_profile();

-- ============================================================================
-- 3. New Governance, Verification, Stories & Platform Control Tables
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.stories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    media_url text,
    caption text DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL DEFAULT (now() + interval '24 hours')
);

CREATE TABLE IF NOT EXISTS public.verification_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    requested_tier text NOT NULL DEFAULT 'creator' CHECK (requested_tier IN ('creator', 'business')),
    reason text NOT NULL DEFAULT '',
    status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    review_notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz
);

CREATE TABLE IF NOT EXISTS public.admin_permissions (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    can_manage_users boolean NOT NULL DEFAULT true,
    can_manage_verifications boolean NOT NULL DEFAULT true,
    can_moderate_content boolean NOT NULL DEFAULT true,
    can_manage_settings boolean NOT NULL DEFAULT false,
    can_send_announcements boolean NOT NULL DEFAULT false,
    updated_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.platform_announcements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title text NOT NULL,
    body text NOT NULL,
    audience text NOT NULL DEFAULT 'all' CHECK (audience IN ('all', 'verified', 'staff')),
    is_active boolean NOT NULL DEFAULT true,
    created_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.platform_settings (
    id text PRIMARY KEY DEFAULT 'default',
    maintenance_mode boolean NOT NULL DEFAULT false,
    maintenance_message text NOT NULL DEFAULT 'VYBAL is undergoing scheduled maintenance.',
    allow_registrations boolean NOT NULL DEFAULT true,
    allow_posting boolean NOT NULL DEFAULT true,
    allow_comments boolean NOT NULL DEFAULT true,
    updated_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO public.platform_settings (id) VALUES ('default') ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.admin_audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
    actor_email text,
    action text NOT NULL,
    target_type text NOT NULL,
    target_id text,
    details text NOT NULL DEFAULT '',
    severity text NOT NULL DEFAULT 'info' CHECK (severity IN ('info', 'warning', 'critical')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_verification_requests_status ON public.verification_requests(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON public.admin_audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_announcements_active ON public.platform_announcements(is_active, created_at DESC);

-- ============================================================================
-- 4. Row Level Security (RLS) Policies
-- ============================================================================

ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verification_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.platform_announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.platform_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_audit_logs ENABLE ROW LEVEL SECURITY;

-- Stories
DROP POLICY IF EXISTS "Stories viewable by everyone" ON public.stories;
CREATE POLICY "Stories viewable by everyone" ON public.stories
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "Users create own stories" ON public.stories;
CREATE POLICY "Users create own stories" ON public.stories
    FOR INSERT WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Owners or staff delete stories" ON public.stories;
CREATE POLICY "Owners or staff delete stories" ON public.stories
    FOR DELETE USING (auth.uid() = user_id OR public.vybal_is_staff());

-- Verification Requests
DROP POLICY IF EXISTS "Users view own or staff view all verification requests" ON public.verification_requests;
CREATE POLICY "Users view own or staff view all verification requests" ON public.verification_requests
    FOR SELECT USING (auth.uid() = user_id OR public.vybal_is_staff());

DROP POLICY IF EXISTS "Users submit own verification requests" ON public.verification_requests;
CREATE POLICY "Users submit own verification requests" ON public.verification_requests
    FOR INSERT WITH CHECK (auth.uid() = user_id AND status = 'pending');

DROP POLICY IF EXISTS "Staff update verification requests" ON public.verification_requests;
CREATE POLICY "Staff update verification requests" ON public.verification_requests
    FOR UPDATE USING (public.vybal_is_staff());

-- Admin Permissions (Super Admin only for writes)
DROP POLICY IF EXISTS "Staff read admin permissions" ON public.admin_permissions;
CREATE POLICY "Staff read admin permissions" ON public.admin_permissions
    FOR SELECT USING (public.vybal_is_staff());

DROP POLICY IF EXISTS "Super Admin manages admin permissions" ON public.admin_permissions;
CREATE POLICY "Super Admin manages admin permissions" ON public.admin_permissions
    FOR ALL USING (public.vybal_is_super_admin()) WITH CHECK (public.vybal_is_super_admin());

-- Platform Announcements
DROP POLICY IF EXISTS "Active announcements visible to all" ON public.platform_announcements;
CREATE POLICY "Active announcements visible to all" ON public.platform_announcements
    FOR SELECT USING (is_active = true OR public.vybal_is_staff());

DROP POLICY IF EXISTS "Super Admin or Admin manages announcements" ON public.platform_announcements;
CREATE POLICY "Super Admin or Admin manages announcements" ON public.platform_announcements
    FOR ALL USING (public.vybal_caller_role() IN ('super_admin', 'admin'))
    WITH CHECK (public.vybal_caller_role() IN ('super_admin', 'admin'));

-- Platform Settings
DROP POLICY IF EXISTS "Platform settings readable by all" ON public.platform_settings;
CREATE POLICY "Platform settings readable by all" ON public.platform_settings
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "Super Admin updates platform settings" ON public.platform_settings;
CREATE POLICY "Super Admin updates platform settings" ON public.platform_settings
    FOR UPDATE USING (public.vybal_is_super_admin()) WITH CHECK (public.vybal_is_super_admin());

-- Audit Logs (Read-only for staff, insert by staff)
DROP POLICY IF EXISTS "Staff read audit logs" ON public.admin_audit_logs;
CREATE POLICY "Staff read audit logs" ON public.admin_audit_logs
    FOR SELECT USING (public.vybal_is_staff());

DROP POLICY IF EXISTS "Staff insert audit logs" ON public.admin_audit_logs;
CREATE POLICY "Staff insert audit logs" ON public.admin_audit_logs
    FOR INSERT WITH CHECK (public.vybal_is_staff());

-- Staff moderation override on post_reports, posts, comments (if tables exist)
DO $$
BEGIN
    IF to_regclass('public.post_reports') IS NOT NULL THEN
        EXECUTE 'DROP POLICY IF EXISTS "Staff view all post reports" ON public.post_reports';
        EXECUTE 'CREATE POLICY "Staff view all post reports" ON public.post_reports FOR SELECT USING (public.vybal_is_staff())';
        EXECUTE 'DROP POLICY IF EXISTS "Staff delete resolved post reports" ON public.post_reports';
        EXECUTE 'CREATE POLICY "Staff delete resolved post reports" ON public.post_reports FOR DELETE USING (public.vybal_is_staff())';
    END IF;
END $$;

-- ============================================================================
-- 5. Server-Side Privileged RPC Functions (Enforced via auth.uid())
-- ============================================================================

CREATE OR REPLACE FUNCTION public.admin_get_dashboard_metrics()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_total_users bigint := 0;
    v_active_users bigint := 0;
    v_online_users bigint := 0;
    v_total_posts bigint := 0;
    v_total_videos bigint := 0;
    v_total_stories bigint := 0;
    v_pending_verifications bigint := 0;
    v_reported_content bigint := 0;
    v_banned_accounts bigint := 0;
    v_new_users_7d bigint := 0;
    v_new_posts_7d bigint := 0;
BEGIN
    IF NOT public.vybal_is_staff() THEN
        RAISE EXCEPTION 'Access denied: administrator privileges required.';
    END IF;

    SELECT count(*) INTO v_total_users FROM public.profiles;
    SELECT count(*) INTO v_active_users FROM public.profiles WHERE account_status = 'active';
    SELECT count(*) INTO v_online_users FROM public.profiles WHERE last_seen_at >= (now() - interval '15 minutes');
    SELECT count(*) INTO v_banned_accounts FROM public.profiles WHERE account_status = 'banned';
    SELECT count(*) INTO v_new_users_7d FROM auth.users WHERE created_at >= (now() - interval '7 days');

    IF to_regclass('public.posts') IS NOT NULL THEN
        SELECT count(*) INTO v_total_posts FROM public.posts;
        SELECT count(*) INTO v_total_videos FROM public.posts
            WHERE lower(coalesce(media_url, '')) ~ '\.(mp4|mov|webm|mkv)(\?|$)';
        SELECT count(*) INTO v_new_posts_7d FROM public.posts WHERE created_at >= (now() - interval '7 days');
    END IF;

    SELECT count(*) INTO v_total_stories FROM public.stories;
    SELECT count(*) INTO v_pending_verifications FROM public.verification_requests WHERE status = 'pending';

    IF to_regclass('public.post_reports') IS NOT NULL THEN
        SELECT count(*) INTO v_reported_content FROM public.post_reports;
    END IF;

    RETURN jsonb_build_object(
        'total_users', v_total_users,
        'active_users', v_active_users,
        'online_users', v_online_users,
        'total_posts', v_total_posts,
        'total_videos', v_total_videos,
        'total_stories', v_total_stories,
        'pending_verifications', v_pending_verifications,
        'reported_content', v_reported_content,
        'banned_accounts', v_banned_accounts,
        'new_users_7d', v_new_users_7d,
        'new_posts_7d', v_new_posts_7d
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_update_user(
    p_target_user_id uuid,
    p_display_name text DEFAULT NULL,
    p_bio text DEFAULT NULL,
    p_role text DEFAULT NULL,
    p_is_verified boolean DEFAULT NULL,
    p_verification_tier text DEFAULT NULL,
    p_account_status text DEFAULT NULL,
    p_restriction_reason text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_caller_role text := public.vybal_caller_role();
    v_caller_email text;
    v_target_email text;
BEGIN
    IF v_caller_role NOT IN ('super_admin', 'admin') THEN
        RAISE EXCEPTION 'Access denied: admin or super_admin required.';
    END IF;

    SELECT email INTO v_caller_email FROM auth.users WHERE id = auth.uid();
    SELECT email INTO v_target_email FROM auth.users WHERE id = p_target_user_id;

    -- Super Admin account cannot be modified by anyone else
    IF public.vybal_is_permanent_super_admin_email(v_target_email) AND v_caller_role <> 'super_admin' THEN
        RAISE EXCEPTION 'Protected Super Admin account cannot be modified.';
    END IF;

    -- Only Super Admin can assign or remove administrator/moderator roles
    IF p_role IS NOT NULL AND v_caller_role <> 'super_admin' THEN
        RAISE EXCEPTION 'Only the Super Admin can assign or remove administrator roles.';
    END IF;

    UPDATE public.profiles
    SET
        display_name = COALESCE(p_display_name, display_name),
        bio = COALESCE(p_bio, bio),
        role = COALESCE(p_role, role),
        is_verified = COALESCE(p_is_verified, is_verified),
        verification_tier = CASE
            WHEN p_is_verified = false THEN NULL
            WHEN p_verification_tier IS NOT NULL THEN p_verification_tier
            ELSE verification_tier
        END,
        account_status = COALESCE(p_account_status, account_status),
        restriction_reason = CASE
            WHEN p_account_status = 'active' THEN NULL
            WHEN p_restriction_reason IS NOT NULL THEN p_restriction_reason
            ELSE restriction_reason
        END
    WHERE id = p_target_user_id;

    INSERT INTO public.admin_audit_logs (actor_id, actor_email, action, target_type, target_id, details, severity)
    VALUES (
        auth.uid(),
        v_caller_email,
        'update_user',
        'user',
        p_target_user_id::text,
        format('role=%s verified=%s tier=%s status=%s', p_role, p_is_verified, p_verification_tier, p_account_status),
        CASE WHEN p_account_status IN ('banned', 'suspended') OR p_role IS NOT NULL THEN 'warning' ELSE 'info' END
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_delete_user_account(p_target_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_caller_email text;
    v_target_email text;
BEGIN
    IF NOT public.vybal_is_super_admin() THEN
        RAISE EXCEPTION 'Access denied: only the Super Admin can delete user accounts.';
    END IF;

    SELECT email INTO v_caller_email FROM auth.users WHERE id = auth.uid();
    SELECT email INTO v_target_email FROM auth.users WHERE id = p_target_user_id;

    IF public.vybal_is_permanent_super_admin_email(v_target_email) THEN
        RAISE EXCEPTION 'Protected Super Admin account cannot be deleted.';
    END IF;

    DELETE FROM public.profiles WHERE id = p_target_user_id;
    DELETE FROM auth.users WHERE id = p_target_user_id;

    INSERT INTO public.admin_audit_logs (actor_id, actor_email, action, target_type, target_id, details, severity)
    VALUES (
        auth.uid(),
        v_caller_email,
        'delete_user_account',
        'user',
        p_target_user_id::text,
        format('Deleted account %s', coalesce(v_target_email, p_target_user_id::text)),
        'critical'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_review_verification_request(
    p_request_id uuid,
    p_decision text,
    p_badge_tier text DEFAULT 'creator',
    p_notes text DEFAULT ''
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_req public.verification_requests%ROWTYPE;
    v_caller_email text;
BEGIN
    IF public.vybal_caller_role() NOT IN ('super_admin', 'admin') THEN
        RAISE EXCEPTION 'Access denied: admin or super_admin required.';
    END IF;

    SELECT * INTO v_req FROM public.verification_requests WHERE id = p_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Verification request not found.';
    END IF;

    SELECT email INTO v_caller_email FROM auth.users WHERE id = auth.uid();

    UPDATE public.verification_requests
    SET status = p_decision,
        reviewed_by = auth.uid(),
        review_notes = p_notes,
        reviewed_at = now()
    WHERE id = p_request_id;

    IF p_decision = 'approved' THEN
        UPDATE public.profiles
        SET is_verified = true,
            verification_tier = coalesce(NULLIF(p_badge_tier, ''), v_req.requested_tier)
        WHERE id = v_req.user_id;
    END IF;

    INSERT INTO public.admin_audit_logs (actor_id, actor_email, action, target_type, target_id, details, severity)
    VALUES (
        auth.uid(),
        v_caller_email,
        'review_verification_' || p_decision,
        'verification_request',
        p_request_id::text,
        format('user=%s tier=%s notes=%s', v_req.user_id, p_badge_tier, p_notes),
        'info'
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_moderate_content(
    p_content_type text,
    p_content_id uuid,
    p_report_id uuid DEFAULT NULL,
    p_action text DEFAULT 'remove',
    p_reason text DEFAULT ''
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_caller_email text;
BEGIN
    IF NOT public.vybal_is_staff() THEN
        RAISE EXCEPTION 'Access denied: staff moderation privileges required.';
    END IF;

    SELECT email INTO v_caller_email FROM auth.users WHERE id = auth.uid();

    IF p_action = 'remove' THEN
        IF p_content_type = 'post' AND to_regclass('public.posts') IS NOT NULL THEN
            DELETE FROM public.posts WHERE id = p_content_id;
        ELSIF p_content_type = 'comment' AND to_regclass('public.comments') IS NOT NULL THEN
            DELETE FROM public.comments WHERE id = p_content_id;
        ELSIF p_content_type = 'story' THEN
            DELETE FROM public.stories WHERE id = p_content_id;
        END IF;
    END IF;

    IF p_report_id IS NOT NULL AND to_regclass('public.post_reports') IS NOT NULL THEN
        DELETE FROM public.post_reports WHERE id = p_report_id;
    END IF;

    INSERT INTO public.admin_audit_logs (actor_id, actor_email, action, target_type, target_id, details, severity)
    VALUES (
        auth.uid(),
        v_caller_email,
        'moderate_' || p_action,
        p_content_type,
        p_content_id::text,
        coalesce(p_reason, ''),
        CASE WHEN p_action = 'remove' THEN 'warning' ELSE 'info' END
    );
END;
$$;
