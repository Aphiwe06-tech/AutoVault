package core;

import model.BackupJob;
import model.BackupVersion;

import java.io.File;
import java.util.Collections;
import java.util.List;


public class RetentionPolicy {
    
    
 private int maxVersions;

   public RetentionPolicy( int maxVersions )
   {
      if ( maxVersions < 1 )
         throw new IllegalArgumentException( "maxVersions must be at least 1" );
      this.maxVersions = maxVersions;
   } 

   public int getMaxVersions() { return maxVersions; }

   // Removes the oldest versions until at most maxVersions remain.
  
   
   public int enforce( BackupJob job )
   {
      List<BackupVersion> versions = job.getVersions();
      Collections.sort( versions ); // oldest version first, uses compareTo of BackupVersion: O(n log n).

      int removed = 0;
      while ( versions.size() > maxVersions )
      {
         BackupVersion oldest = versions.remove( 0 );
         job.removeVersion( oldest ); // also remove it from the job itself (getVersions returns a copy)
         deleteRecursive( oldest.getVersionDir() );
         removed++;
         // TODO: replace with BackupLogger once it is ready
         System.out.printf( "[Retention] Removed old version: %s%n", oldest );
      } 

      return removed;
   } 
   
   /* Recursion step: delete everything inside a folder first (each item is a
    smaller version of the same problem), then delete the empty folder. 
   */
   private void deleteRecursive( File dir )
   {
      if ( dir == null || !dir.exists() ) //a plain file (nothing inside it), so just delete it.
         return;

      if ( dir.isDirectory() )
      {
         File[] children = dir.listFiles(); // null if folder cannot be read
         if ( children != null )
         {
            for ( File child : children )
               deleteRecursive( child );
         } 
      } 

      try
      {
         if ( !dir.delete() )
            System.err.printf( "[Retention] Could not delete: %s%n", dir );
      } 
      catch ( SecurityException securityException )
      {
         System.err.printf( "[Retention] No permission to delete: %s%n", dir );
      } 
   } 
}