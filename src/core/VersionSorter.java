
package core;

import model.BackupVersion;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class VersionSorter {
    //Sorts backup versions from oldest to newest using mergeSort
    public static List<BackupVersion> mergeSort(List<BackupVersion> list) {
        
        if (list.size() <= 1) {// a list with 0 or 1 items is already sorted
            return new ArrayList<>(list);
        }
        
        int mid = list.size() / 2; //finding the middle and dividing into two halves
        List<BackupVersion> left = mergeSort(list.subList(0, mid));
        List<BackupVersion> right = mergeSort(list.subList(mid, list.size()));
        
        //combine the two halves
        return merge(left, right);
    }

    //Merges two already-sorted lists into one sorted list
    private static List<BackupVersion> merge(List<BackupVersion> left,
                                             List<BackupVersion> right) {
        List<BackupVersion> result = new ArrayList<>();
        int i = 0;//current position in the left list
        int j = 0;//current position in the right list
        
        while (i < left.size() && j < right.size()) { // Compare the front items of both lists and take the older one
            if (left.get(i).compareTo(right.get(j)) <= 0) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        // One list is now empty copy what remains of the other
        while (i < left.size())  result.add(left.get(i++));
        while (j < right.size()) result.add(right.get(j++));
        return result;
    }

    //Finds the most recent version created at or before a given time
    public static int binarySearch(List<BackupVersion> list, LocalDateTime t) {
        int low = 0; // start of the search range
        int high = list.size() - 1; // end of the search range
        int answer = -1;
        
        while (low <= high) {
            int mid = (low + high) / 2;// look at the middle item
            if (!list.get(mid).getTimestamp().isAfter(t)) { //This version is not newer than t, so it qualifies
                answer = mid;     
                low = mid + 1;
                
            } else { //This version is newer than t so search the left half
                high = mid - 1;
            }
        }
        return answer;
    }
}
