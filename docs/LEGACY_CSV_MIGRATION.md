# Legacy CSV phone migration

This runbook covers the one-time migration from an old real-phone GymTrack install using the legacy package ID `com.example.gymtrack` into the permanent-ID app `app.znypr.gymtrack`.

## Safety rules

- Do not uninstall or clear data from the old `com.example.gymtrack` app until the new app has imported and verified all workouts.
- Keep the exported CSV folder backed up outside the phone.
- Install and validate on an emulator before changing the real phone.
- Use a clean export folder. Do not mix new exports with older CSV files already present in Downloads.

## Known source export

Use the clean export folder:

```text
C:\Users\znypr\Downloads\gymtrack-exports-2
```

Current clean export result:

```text
Total CSV files: 38
Expected imported workouts: 38
```

An earlier folder, `C:\Users\znypr\Downloads\gymtrack-exports`, contained old overlapping exports and produced misleading counts. Do not use it for migration.

Representative structure:

```csv
Title,Category,Timestamp,Learnings
,Legs,30/06/2026 16:32,
Main Index,Main Entry,Time,Flag
0,seated hamstrings cy,0'00'',bi
1,reverse hacksquat,16'40'',bi
2,leg press,56'55'',bi
3,leg extension RL,57'25'',uni
Main Index,Sub Entry,Time,Flag
0,10x 67kg (0'05''),0'05'',1x
```

## Prepare a protected copy

From Windows PowerShell:

```powershell
Compress-Archive `
  -Path "C:\Users\znypr\Downloads\gymtrack-exports-2\*.csv" `
  -DestinationPath "C:\Users\znypr\Downloads\gymtrack-clean-phone-exports-backup.zip" `
  -Force

Get-Item "C:\Users\znypr\Downloads\gymtrack-clean-phone-exports-backup.zip"
```

## Preflight uniqueness check

From Windows PowerShell:

```powershell
$dir = "C:\Users\znypr\Downloads\gymtrack-exports-2"
$files = Get-ChildItem $dir -Filter *.csv

"Total CSV files: $($files.Count)"
"Unique file contents: $((Get-FileHash $files.FullName -Algorithm SHA256 | Select-Object -ExpandProperty Hash -Unique).Count)"

$rows = foreach ($file in $files) {
  $firstTwoLines = Get-Content $file.FullName -TotalCount 2
  $meta = $firstTwoLines | ConvertFrom-Csv
  [pscustomobject]@{
    File = $file.Name
    Hash = (Get-FileHash $file.FullName -Algorithm SHA256).Hash
    Timestamp = $meta.Timestamp
    Category = $meta.Category
    Title = $meta.Title
    Size = $file.Length
  }
}

"Unique timestamps: $(($rows | Select-Object -ExpandProperty Timestamp -Unique).Count)"
```

## Import behavior

- Exact duplicate records are skipped.
- CSVs are grouped by parsed timestamp.
- From each timestamp group, the importer keeps the most complete snapshot by note length, set-like line count, row metadata length, and file size.
- Older/incomplete snapshots in the same timestamp group are skipped.
- If a selected CSV timestamp already exists locally, it is skipped to prevent re-import duplicates.
- The import summary reports imported count, exact duplicate count, older snapshot count, existing timestamp count, and failed count.
- Individual file failures do not delete already imported records.

Expected first clean import shape for the known clean export folder:

```text
CSV import: 38/38 imported, 0 failed
```

Expected second import of the same folder:

```text
CSV import: 0/38 imported, 38 existing timestamps skipped, 0 failed
```

The exact duplicate and existing timestamp wording may differ slightly if the folder changes.

## Emulator validation first

1. Install the new app on an emulator.
2. Copy the clean `gymtrack-exports-2` folder or ZIP contents into a document-provider location visible to the emulator.
3. Open GymTrack.
4. Use the import button on the notes screen.
5. Select all legacy CSV files in one operation.
6. Wait for the import summary toast.
7. Confirm the first import reports 38 imported and 0 failed.
8. Confirm a second import reports 0 imported.
9. Open representative old workouts and verify category, timestamp, main exercises, sub entries, row times, and unilateral/bilateral flags.
10. Open statistics and verify imported workouts are included.

## Real phone migration

Only after emulator validation:

1. Keep the old `com.example.gymtrack` app installed.
2. Install the permanent-ID app `app.znypr.gymtrack` as a separate app.
3. Copy the protected clean export folder or ZIP contents to the phone.
4. Use the import button on the notes screen.
5. Select all 38 CSV files in one operation.
6. Record the import summary.
7. Verify the note/workout count.
8. Open several representative workouts from different categories.
9. Verify statistics/canonical data after import.
10. Create a new `.gymtrack-backup` from the new app after verification.

## Repeated import behavior

Bulk import skips exact duplicate records and already-imported timestamps. Re-importing the same clean folder should import zero additional workouts after a successful import.

## Failure handling

The importer continues after individual file failures and reports the number of failed files plus the first failed file names. Do not remove the old app until failed files are inspected and recovered or explicitly accepted as not needed.
