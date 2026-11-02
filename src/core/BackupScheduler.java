
package core;

import model.BackupJob;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.PriorityQueue;
import model.ScheduledTask;

public class BackupScheduler implements Runnable {
    
     private static final long CHECK_INTERVAL_MS = 500;

   private PriorityQueue<ScheduledTask> queue = new PriorityQueue<ScheduledTask>();
   private boolean running = true;

   private ArrayList<String> activeJobIds = new ArrayList<String>();
   private BackupManager manager;

   public BackupScheduler( BackupManager manager )
   {
      if ( manager == null )
         throw new IllegalArgumentException( "manager must not be null" );
      this.manager = manager;
   } 

   // schedule a job; replaces any old schedule for the same job
   public synchronized void schedule( BackupJob job )
   {
      ScheduledTask task = new ScheduledTask( job );
      String jobId = job.getJobId();

      removeFromQueue( jobId );
      queue.add( task );
      if ( !activeJobIds.contains( jobId ) )
         activeJobIds.add( jobId );
   } 

   // stops job from running again
   public synchronized void cancel( String jobId )
   {
      activeJobIds.remove( jobId );
      removeFromQueue( jobId );
   } 

   // asks loop to end (it stops within about half a second)
   public synchronized void stop()
   {
      running = false;
   } 

   // the scheduler thread's loop
   public void run()
   {
      while ( isRunning() )
      {
         ScheduledTask dueTask = pollDueTask();

         if ( dueTask == null ) // nothing due yet, wait a little
         {
            try
            {
               Thread.sleep( CHECK_INTERVAL_MS );
            } 
            catch ( InterruptedException interruptedException )
            {
               return; 
            } 
         } 
         else
            runTask( dueTask );
      } 
   } 
   
   private synchronized boolean isRunning()
   {
      return running;
   } 

   // takes front task out of the queue only if it's due
   private synchronized ScheduledTask pollDueTask()
   {
      ScheduledTask front = queue.peek();
      if ( front != null && !front.getNextRun().isAfter( LocalDateTime.now() ) )
         return queue.poll();
      return null;
   } 

   // runs the backup NOT inside a synchronized method, so a slow backup
  
   private void runTask( ScheduledTask task )
   {
      try
      {
         manager.runBackup( task.getJob().getJobId() );
      }
      catch ( Exception exception )
      {
         // one failed backup must not stop the scheduler thread
         // TODO: replace with BackupLogger once it is ready
         System.err.printf( "[Scheduler] Backup failed for %s: %s%n",
            task.getJob().getName(), exception.getMessage() );
      } 

      requeue( task );
   } 

   // put the task back only if the job was not cancelled or re-scheduled
   private synchronized void requeue( ScheduledTask task )
   {
      String jobId = task.getJob().getJobId();
      if ( activeJobIds.contains( jobId ) && !isQueued( jobId ) )
      {
         task.reschedule();
         queue.add( task );
      } 
   } 

   private synchronized boolean isQueued( String jobId )
   {
      Iterator<ScheduledTask> iterator = queue.iterator();
      while ( iterator.hasNext() )
      {
         if ( iterator.next().getJob().getJobId().equals( jobId ) )
            return true;
      } 
      return false;
   } 

   private synchronized void removeFromQueue( String jobId )
   {
      Iterator<ScheduledTask> iterator = queue.iterator();
      while ( iterator.hasNext() )
      {
         if ( iterator.next().getJob().getJobId().equals( jobId ) )
            iterator.remove();
      } 
   } 
} 

    
    

