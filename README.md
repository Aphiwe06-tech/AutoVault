# AutoVault

**Module:** Software Design 2 (SDN260S)
**Group:** 32

**Group Members:**

- Aphiwe Mtshali
- Khano Mavhandu
- Luyema Maqasha
- Orinea Nemahate
- Ampfarisaho Thukhutha
- Kelebogile Moswane

---

## What is AutoVault?

AutoVault is a backup program we built in Java. You tell it which folder to protect and where to keep the backups. It then copies the folder on a schedule, keeps a history of versions, and lets you restore an older version when you need it.

## What it can do

- **Automatic backups:** each job has its own interval in minutes. The scheduler uses a Priority Queue so the job that is due next always runs first.
- **Backup now:** you can also run a backup yourself at any time.
- **Version history:** every backup is saved as its own version, listed from oldest to newest (we sort them with merge sort).
- **Retention:** only the newest 5 versions are kept. Older ones are deleted from the disk automatically.
- **Change detection:** before a backup, AutoVault compares the source folder with the latest version, file by file. If nothing changed, the backup is skipped so we do not store duplicates.
- **Restore:** pick any version and restore it to any folder. The folder is created if it does not exist.
- **Excluded file types:** a job can skip certain extensions, for example .tmp.
- **Saved jobs:** jobs and their versions are saved in jobs.dat, so they are still there after you restart.
- **Logging:** every backup, skip, restore and error is written to AutoVault.log.
- **QA / Logging window:** a button in the main window that opens our logging and QA screen.
- **Input checks:** empty names, intervals below 1 minute, and a backup folder inside the source folder are all rejected.

## What you need

- Java 21
- Apache NetBeans 25

## How to run the application

1. Open the AutoV project in Apache NetBeans.
2. Set ui.Main as the main class.
3. Press **F6** to run. The main AutoVault window opens.
4. Click **Add job**, choose the source folder, the backup folder and the interval, then click **Backup now** to try it.
5. Use **Versions / Restore** to bring back an older version.

 There is also a console menu ui.CliMenu with the same features. ui.Main only starts the window, so the console menu is not opened automatically

## How to see it working (no typing needed)

Two files run on their own. In NetBeans, right-click the file and choose **Run File** (Shift+F6).

- **ui/AutoVaultDemo.java:** a scripted demo. It adds a job, backs up, edits a file, backs up again, lists the versions and restores the oldest one.
- **AutoVaultTests.java:** runs our 25 tests (normal and edge input) and writes the results to test-results.txt.

Our last test run passed **25 out of 25**.

## Project structure

The source code is in the AutoV/src folder and is split into four packages plus one test file.

- **core package:** BackupManager, BackupEngine, BackupScheduler, RestoreService, RetentionPolicy, VersionSorter, FileScanner, ChangeDetector, HashDetector and TimestampDetector.
- **model package:** BackupJob, BackupVersion and ScheduledTask.
- **ui package:** Main, MainWindow, CliMenu, AutoVaultDemo and AutoVaultRole6Prototype.
- **util package:** BackupLogger and LogEntry.
- **Test file:** AutoVaultTests.java, which sits directly in AutoV/src.

## Notes

- Each backup is a full copy of the folder, so backups use more disk space as the folder grows.
- HashDetector and TimestampDetector are in the core package as extra change detectors.

## Documentation

The documents named below are found in the files above.

- User Guide
- UML Diagram
- Testing Evidence
- GA Reflection

## GitHub

https://github.com/Aphiwe06-tech/AutoVault
