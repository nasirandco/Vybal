-- ============================================================================
-- VYBAL CHECKPOINT 3: COMPLETE ENGAGEMENT SYSTEM (NON-DESTRUCTIVE MIGRATION)
-- ============================================================================
-- Review and run this SQL in your Supabase SQL Editor to enable full
-- engagement persistence. Uses IF NOT EXISTS throughout and never alters or
-- drops existing tables, buckets, or data.
-- ============================================================================

-- 1. Optional additive columns on existing posts table
ALTER TABLE public.posts
  ADD COLUMN IF NOT EXISTS is_pinned boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS comments_disabled boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS share_count integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS view_count integer NOT NULL DEFAULT 0;

-- 2. Post likes & reactions (single table prevents duplicate reactions per user)
CREATE TABLE IF NOT EXISTS public.post_reactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  reaction text NOT NULL CHECK (reaction IN ('like', 'love', 'haha', 'wow', 'sad', 'support')),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (post_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_post_reactions_post_id ON public.post_reactions(post_id);
ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "post_reactions_select_all" ON public.post_reactions
    FOR SELECT USING (true);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "post_reactions_insert_own" ON public.post_reactions
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "post_reactions_update_own" ON public.post_reactions
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "post_reactions_delete_own" ON public.post_reactions
    FOR DELETE TO authenticated USING (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 3. Threaded comments & comment likes
CREATE TABLE IF NOT EXISTS public.comments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  parent_comment_id uuid REFERENCES public.comments(id) ON DELETE CASCADE,
  content text NOT NULL CHECK (char_length(trim(content)) > 0 AND char_length(content) <= 1000),
  is_pinned boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_comments_post_id_created ON public.comments(post_id, created_at DESC);
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "comments_select_all" ON public.comments
    FOR SELECT USING (true);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "comments_insert_own" ON public.comments
    FOR INSERT TO authenticated WITH CHECK (
      auth.uid() = user_id
      AND NOT EXISTS (
        SELECT 1 FROM public.posts p WHERE p.id = post_id AND p.comments_disabled = true
      )
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "comments_update_author_or_post_owner" ON public.comments
    FOR UPDATE TO authenticated USING (
      auth.uid() = user_id
      OR EXISTS (SELECT 1 FROM public.posts p WHERE p.id = post_id AND p.user_id = auth.uid())
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "comments_delete_author_or_post_owner" ON public.comments
    FOR DELETE TO authenticated USING (
      auth.uid() = user_id
      OR EXISTS (SELECT 1 FROM public.posts p WHERE p.id = post_id AND p.user_id = auth.uid())
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS public.comment_likes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  comment_id uuid NOT NULL REFERENCES public.comments(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (comment_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_comment_likes_comment_id ON public.comment_likes(comment_id);
ALTER TABLE public.comment_likes ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "comment_likes_select_all" ON public.comment_likes
    FOR SELECT USING (true);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "comment_likes_insert_own" ON public.comment_likes
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "comment_likes_delete_own" ON public.comment_likes
    FOR DELETE TO authenticated USING (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 4. Private saved collections & saved posts (strictly owner-only RLS)
CREATE TABLE IF NOT EXISTS public.saved_collections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 50),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, name)
);
ALTER TABLE public.saved_collections ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "saved_collections_owner_all" ON public.saved_collections
    FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS public.saved_posts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  collection_id uuid REFERENCES public.saved_collections(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, post_id)
);
CREATE INDEX IF NOT EXISTS idx_saved_posts_user_id ON public.saved_posts(user_id, created_at DESC);
ALTER TABLE public.saved_posts ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "saved_posts_owner_all" ON public.saved_posts
    FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 5. Reposts (prevent duplicate reposts per user/post)
CREATE TABLE IF NOT EXISTS public.reposts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  caption text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, post_id)
);
CREATE INDEX IF NOT EXISTS idx_reposts_post_id ON public.reposts(post_id);
ALTER TABLE public.reposts ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "reposts_select_all" ON public.reposts
    FOR SELECT USING (true);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "reposts_insert_own" ON public.reposts
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "reposts_delete_own" ON public.reposts
    FOR DELETE TO authenticated USING (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 6. Follow system (prevents self-follow at DB level)
CREATE TABLE IF NOT EXISTS public.follows (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  follower_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  following_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'accepted' CHECK (status IN ('accepted', 'pending')),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (follower_id, following_id),
  CHECK (follower_id <> following_id)
);
CREATE INDEX IF NOT EXISTS idx_follows_follower ON public.follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_follows_following ON public.follows(following_id);
ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "follows_select_all" ON public.follows
    FOR SELECT USING (true);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "follows_insert_own" ON public.follows
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = follower_id AND follower_id <> following_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "follows_delete_own" ON public.follows
    FOR DELETE TO authenticated USING (auth.uid() = follower_id OR auth.uid() = following_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 7. Hidden posts & post reports (owner-only RLS)
CREATE TABLE IF NOT EXISTS public.hidden_posts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, post_id)
);
ALTER TABLE public.hidden_posts ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "hidden_posts_owner_all" ON public.hidden_posts
    FOR ALL TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS public.post_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  reporter_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  reason text NOT NULL CHECK (char_length(trim(reason)) BETWEEN 2 AND 300),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (post_id, reporter_id)
);
ALTER TABLE public.post_reports ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "post_reports_insert_own" ON public.post_reports
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = reporter_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE POLICY "post_reports_select_own" ON public.post_reports
    FOR SELECT TO authenticated USING (auth.uid() = reporter_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 8. Optional additive columns on existing notifications table for deep navigation
ALTER TABLE public.notifications
  ADD COLUMN IF NOT EXISTS actor_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  ADD COLUMN IF NOT EXISTS post_id uuid REFERENCES public.posts(id) ON DELETE CASCADE;
