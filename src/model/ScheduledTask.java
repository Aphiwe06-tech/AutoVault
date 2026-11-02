

package model;

import model.BackupJob;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;


public class ScheduledTask implements Comparable<ScheduledTask> { 
// Implements Comparable so a PriorityQueue keeps the task that is due
   // Pairs a BackupJob with the time it must run next.
    
 public static long millisPerMinute = 60000;

   private BackupJob job;
   private LocalDateTime nextRun;

   public ScheduledTask( BackupJob job )
   {
      if ( job == null )
         throw new IllegalArgumentException( "job must not be null" );
      if ( job.getIntervalMinutes() <= 0 )
         throw new IllegalArgumentException( "interval must be greater than 0" );

      this.job = job;
      reschedule();
   } 

   // next run = now + one interval
   public void reschedule()
   {
      long intervalMillis = job.getIntervalMinutes() * millisPerMinute;
      nextRun = LocalDateTime.now().plus( intervalMillis, ChronoUnit.MILLIS );
   } 

   public BackupJob getJob() { return job; }
   public LocalDateTime getNextRun() { return nextRun; }

   // earlier run time = higher priority (front of the PriorityQueue)
   public int compareTo( ScheduledTask other )
   {
      return nextRun.compareTo( other.nextRun );
   } 
} 
