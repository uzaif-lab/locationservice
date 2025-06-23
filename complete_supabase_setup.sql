-- ============================================================================
-- COMPLETE SUPABASE SETUP FOR CHILD LOCATION TRACKER
-- ONE SCRIPT TO RULE THEM ALL - INCLUDES EVERYTHING
-- Run this SINGLE script in your Supabase SQL Editor
-- ============================================================================

-- ============================================================================
-- 1. CLEANUP EXISTING SETUP (if any)
-- ============================================================================
-- Drop existing triggers and functions to avoid conflicts
DROP TRIGGER IF EXISTS trigger_auto_cleanup_locations ON public.locations;
DROP TRIGGER IF EXISTS trigger_cleanup_old_locations ON public.locations;
DROP FUNCTION IF EXISTS auto_cleanup_old_locations();
DROP FUNCTION IF EXISTS cleanup_old_locations();
DROP FUNCTION IF EXISTS cleanup_keep_last_n_locations();
DROP FUNCTION IF EXISTS cleanup_old_locations_by_time();
DROP FUNCTION IF EXISTS manual_cleanup_all_locations();
DROP FUNCTION IF EXISTS manual_cleanup_by_age(INTEGER);
DROP FUNCTION IF EXISTS get_latest_location(TEXT);
DROP VIEW IF EXISTS recent_locations;

-- Drop existing policies
DROP POLICY IF EXISTS "Allow anonymous insert locations" ON public.locations;
DROP POLICY IF EXISTS "Allow anonymous select locations" ON public.locations;
DROP POLICY IF EXISTS "Allow anonymous location insert" ON public.locations;
DROP POLICY IF EXISTS "Allow anonymous location select" ON public.locations;
DROP POLICY IF EXISTS "Allow authenticated users full access" ON public.locations;
DROP POLICY IF EXISTS "Restrict anonymous updates" ON public.locations;
DROP POLICY IF EXISTS "Restrict anonymous deletes" ON public.locations;

-- ============================================================================
-- 2. CREATE LOCATIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.locations (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    child_id TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL,
    speed REAL,
    bearing REAL,
    location_timestamp TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- Add constraints for data validation
    CONSTRAINT valid_latitude CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT valid_longitude CHECK (longitude >= -180 AND longitude <= 180),
    CONSTRAINT valid_accuracy CHECK (accuracy IS NULL OR accuracy >= 0),
    CONSTRAINT valid_speed CHECK (speed IS NULL OR speed >= 0)
);

-- ============================================================================
-- 3. CREATE PERFORMANCE INDEXES
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_locations_child_id ON public.locations(child_id);
CREATE INDEX IF NOT EXISTS idx_locations_created_at ON public.locations(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_locations_child_created ON public.locations(child_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_locations_timestamp ON public.locations(location_timestamp DESC);

-- ============================================================================
-- 4. ENABLE ROW LEVEL SECURITY (RLS)
-- ============================================================================
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- 5. CREATE RLS POLICIES FOR SECURITY
-- ============================================================================

-- Policy 1: Allow anonymous users to INSERT location data (for Android app)
CREATE POLICY "Allow anonymous insert locations" 
ON public.locations 
FOR INSERT 
TO anon 
WITH CHECK (true);

-- Policy 2: Allow anonymous users to SELECT location data (for real-time & API)
CREATE POLICY "Allow anonymous select locations" 
ON public.locations 
FOR SELECT 
TO anon 
USING (true);

-- Policy 3: Allow authenticated users full access (for dashboards/admin)
CREATE POLICY "Allow authenticated users full access" 
ON public.locations 
FOR ALL 
TO authenticated 
USING (true) 
WITH CHECK (true);

-- Policy 4: Restrict anonymous users from updating data
CREATE POLICY "Restrict anonymous updates" 
ON public.locations 
FOR UPDATE 
TO anon 
USING (false);

-- Policy 5: Restrict anonymous users from deleting data
CREATE POLICY "Restrict anonymous deletes" 
ON public.locations 
FOR DELETE 
TO anon 
USING (false);

-- ============================================================================
-- 6. ENABLE REAL-TIME SUBSCRIPTIONS
-- ============================================================================
-- Add table to real-time publication (with error handling)
DO $$
BEGIN
    -- Check if table is already in publication
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' 
        AND tablename = 'locations'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.locations;
        RAISE NOTICE 'Table locations added to supabase_realtime publication';
    ELSE
        RAISE NOTICE 'Table locations already in supabase_realtime publication - skipping';
    END IF;
END $$;

-- ============================================================================
-- 7. AUTOMATIC CLEANUP SYSTEM (PRIVACY FOCUSED)
-- ============================================================================
-- This automatically deletes old locations when new ones are added
-- Keeps only the most recent location per child for maximum privacy

-- Create the automatic cleanup function
CREATE OR REPLACE FUNCTION auto_cleanup_old_locations()
RETURNS TRIGGER AS $$
BEGIN
    -- Delete all previous locations for this child_id
    -- Keep only the newly inserted record
    DELETE FROM public.locations 
    WHERE child_id = NEW.child_id 
    AND id != NEW.id;
    
    -- Log the cleanup action
    RAISE NOTICE 'Auto-cleaned old locations for child_id: %, kept latest location', NEW.child_id;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create trigger that runs AFTER INSERT to automatically clean up old locations
CREATE TRIGGER trigger_auto_cleanup_locations
    AFTER INSERT ON public.locations
    FOR EACH ROW
    EXECUTE FUNCTION auto_cleanup_old_locations();

-- ============================================================================
-- 8. MANUAL CLEANUP FUNCTIONS (FOR MAINTENANCE)
-- ============================================================================

-- Function to clean up all old locations manually (keep only latest per child)
CREATE OR REPLACE FUNCTION manual_cleanup_all_locations()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete all but the most recent location for each child
    WITH latest_locations AS (
        SELECT DISTINCT ON (child_id) id, child_id
        FROM public.locations 
        ORDER BY child_id, created_at DESC
    )
    DELETE FROM public.locations 
    WHERE id NOT IN (SELECT id FROM latest_locations);
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Manual cleanup completed. Deleted % old location records', deleted_count;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to clean up locations older than specified hours
CREATE OR REPLACE FUNCTION manual_cleanup_by_age(hours_to_keep INTEGER DEFAULT 24)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_time TIMESTAMPTZ;
BEGIN
    cutoff_time := NOW() - (hours_to_keep || ' hours')::INTERVAL;
    
    DELETE FROM public.locations WHERE created_at < cutoff_time;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RAISE NOTICE 'Manual cleanup completed. Deleted % location records older than % hours', 
                 deleted_count, hours_to_keep;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ============================================================================
-- 9. UTILITY FUNCTIONS
-- ============================================================================

-- Function to get the latest location for a child
CREATE OR REPLACE FUNCTION get_latest_location(p_child_id TEXT)
RETURNS TABLE (
    id UUID,
    child_id TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy REAL,
    speed REAL,
    bearing REAL,
    location_timestamp TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    minutes_ago NUMERIC
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN QUERY
    SELECT 
        l.id,
        l.child_id,
        l.latitude,
        l.longitude,
        l.accuracy,
        l.speed,
        l.bearing,
        l.location_timestamp,
        l.created_at,
        EXTRACT(EPOCH FROM (NOW() - l.created_at)) / 60 AS minutes_ago
    FROM public.locations l
    WHERE l.child_id = p_child_id
    ORDER BY l.created_at DESC
    LIMIT 1;
END;
$$;

-- Create a view for recent locations (last 24 hours)
CREATE OR REPLACE VIEW recent_locations AS
SELECT 
    id,
    child_id,
    latitude,
    longitude,
    accuracy,
    speed,
    bearing,
    location_timestamp,
    created_at,
    -- Calculate age of the location update
    EXTRACT(EPOCH FROM (NOW() - created_at)) / 60 AS minutes_ago
FROM public.locations
WHERE created_at >= NOW() - INTERVAL '24 hours'
ORDER BY created_at DESC;

-- ============================================================================
-- 10. GRANT PERMISSIONS
-- ============================================================================
GRANT USAGE ON SCHEMA public TO anon;
GRANT SELECT, INSERT ON public.locations TO anon;
GRANT SELECT ON recent_locations TO anon;
GRANT ALL ON public.locations TO authenticated;

-- ============================================================================
-- 11. INSERT SAMPLE DATA FOR TESTING
-- ============================================================================
INSERT INTO public.locations (child_id, latitude, longitude, location_timestamp, accuracy, speed)
VALUES (
    'child_001',
    37.7749,  -- San Francisco latitude
    -122.4194, -- San Francisco longitude
    NOW(),
    10.0,     -- 10 meters accuracy
    0.0       -- 0 speed (stationary)
) ON CONFLICT DO NOTHING;

-- ============================================================================
-- 12. VERIFICATION AND TESTING
-- ============================================================================

-- Test 1: Check if table was created successfully
SELECT 'Table created successfully' AS status 
WHERE EXISTS (
    SELECT 1 FROM information_schema.tables 
    WHERE table_name = 'locations' AND table_schema = 'public'
);

-- Test 2: Check if indexes were created
SELECT 'Indexes created: ' || COUNT(*)::TEXT AS status
FROM pg_indexes 
WHERE tablename = 'locations';

-- Test 3: Check if RLS is enabled
SELECT 'RLS enabled: ' || CASE WHEN rowsecurity THEN 'YES' ELSE 'NO' END AS status
FROM pg_tables 
WHERE tablename = 'locations';

-- Test 4: Check RLS policies count
SELECT 'RLS policies created: ' || COUNT(*)::TEXT AS status
FROM pg_policies 
WHERE tablename = 'locations';

-- Test 5: Verify real-time is enabled
SELECT 'Real-time enabled: ' || CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END AS status
FROM pg_publication_tables 
WHERE pubname = 'supabase_realtime' AND tablename = 'locations';

-- Test 6: Verify auto-cleanup trigger exists
SELECT 'Auto-cleanup trigger: ' || CASE WHEN COUNT(*) > 0 THEN 'ACTIVE' ELSE 'MISSING' END AS status
FROM information_schema.triggers 
WHERE trigger_name = 'trigger_auto_cleanup_locations' AND table_name = 'locations';

-- Test 7: Count current locations
SELECT 'Current location records: ' || COUNT(*)::TEXT AS status
FROM public.locations;

-- Test 8: Test auto-cleanup functionality
-- Insert multiple test locations to verify only the latest is kept
INSERT INTO public.locations (child_id, latitude, longitude, location_timestamp, accuracy) 
VALUES 
    ('test_auto_cleanup', 40.7128, -74.0060, NOW() - INTERVAL '3 minutes', 5.0),
    ('test_auto_cleanup', 40.7589, -73.9851, NOW() - INTERVAL '2 minutes', 8.0),
    ('test_auto_cleanup', 40.7614, -73.9776, NOW() - INTERVAL '1 minute', 10.0),
    ('test_auto_cleanup', 40.7505, -73.9934, NOW(), 12.0);

-- Verify only 1 location remains for test_auto_cleanup (should be the latest one)
SELECT 
    'Auto-cleanup test: ' || 
    CASE 
        WHEN COUNT(*) = 1 THEN 'PASSED (only 1 location kept)'
        ELSE 'FAILED (' || COUNT(*)::TEXT || ' locations found)'
    END AS status
FROM public.locations 
WHERE child_id = 'test_auto_cleanup';

-- Show the remaining test location details
SELECT 
    'Test location kept - Lat: ' || latitude::TEXT || ', Lng: ' || longitude::TEXT AS details
FROM public.locations 
WHERE child_id = 'test_auto_cleanup';

-- Clean up test data
DELETE FROM public.locations WHERE child_id = 'test_auto_cleanup';

-- Test 9: Test utility functions
SELECT 'Latest location function: ' || 
       CASE WHEN COUNT(*) > 0 THEN 'WORKING' ELSE 'FAILED' END AS status
FROM get_latest_location('child_001');

-- ============================================================================
-- 13. FINAL STATUS SUMMARY
-- ============================================================================
SELECT '=== SETUP COMPLETE ===' AS message;
SELECT 'Your location tracking system is ready!' AS message;
SELECT 'Features enabled:' AS message;
SELECT '✅ Table created with constraints' AS feature;
SELECT '✅ Performance indexes added' AS feature;
SELECT '✅ Row Level Security enabled' AS feature;
SELECT '✅ Real-time subscriptions active' AS feature;
SELECT '✅ Auto-cleanup trigger active' AS feature;
SELECT '✅ Privacy-focused (only latest location kept)' AS feature;
SELECT '✅ Manual cleanup functions available' AS feature;
SELECT '✅ Utility functions created' AS feature;

-- ============================================================================
-- 14. USAGE EXAMPLES
-- ============================================================================
/*
-- Manual cleanup examples:
SELECT manual_cleanup_all_locations();
SELECT manual_cleanup_by_age(12);

-- Get latest location:
SELECT * FROM get_latest_location('child_001');

-- View recent locations:
SELECT * FROM recent_locations;

-- Check current data:
SELECT child_id, COUNT(*) as location_count FROM public.locations GROUP BY child_id;

-- Disable auto-cleanup (if needed):
DROP TRIGGER IF EXISTS trigger_auto_cleanup_locations ON public.locations;

-- Re-enable auto-cleanup:
CREATE TRIGGER trigger_auto_cleanup_locations
    AFTER INSERT ON public.locations
    FOR EACH ROW
    EXECUTE FUNCTION auto_cleanup_old_locations();
*/

COMMIT; 