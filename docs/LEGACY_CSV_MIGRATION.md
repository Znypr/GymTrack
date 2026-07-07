# Legacy CSV phone migration

This runbook covers the one-time migration from an old real-phone GymTrack install using the legacy package ID `com.example.gymtrack` into the permanent-ID app `app.znypr.gymtrack`.

## Safety rules

- Do not uninstall or clear data from the old `com.example.gymtrack` app until the new app has imported and verified all workouts.
- Keep the exported CSV folder backed up outside the phone.
- Install and validate on an emulator before changing the real phone.

## Known source export

The old phone export is a folder of individual CSV files:

```text
C:\Users\znypr\Downloads\gymtrack-exports
```

Current preflight result:

```text
Total CSV files: 736
Unique file contents: 631
Unique timestamps: 23
Smallest file: 144 bytes
```

The low timestamp count is expected for this export set and must not be treated as only 23 workouts. Many distinct CSV files share the same minute-level timestamp. The bulk importer preserves distinct timestamp collisions by assigning a free one-second offset near the original timestamp.

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
  -Path "C:\Users\znypr\Downloads\gymtrack-exports\*.csv" `
  -DestinationPath "C:\Users\znypr\Downloads\gymtrack-old-phone-exports-backup.zip" `
  -Force

Get-Item "C:\Users\znypr\Downloads\gymtrack-old-phone-exports-backup.zip"
```

## Preflight uniqueness check

From Windows PowerShell:

```powershell
$dir = "C:\Users\znypr\Downloads\gymtrack-exports"
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
- Distinct records with the same parsed timestamp are preserved.
- Timestamp-collision imports receive a deterministic one-second offset near the original timestamp.
- The import summary reports imported count, exact duplicate count, adjusted timestamp-collision count, and failed count.
- Individual file failures do not delete already imported records.

Expected first clean import shape for the known export folder is roughly:

```text
CSV import: 631/736 imported, 105 exact duplicates skipped, 608 timestamp collisions preserved, 0 failed
```

The exact duplicate and timestamp-collision numbers may differ if the export folder changes.

## Emulator validation first

1. Install the new app on an emulator.
2. Copy the `gymtrack-exports` folder or ZIP contents into a document-provider location visible to the emulator.
3. Open GymTrack.
4. Use the import button on the notes screen.
5. Select all legacy CSV files in one operation.
6. Wait for the import summary toast.
7. Confirm the toast reports an import count close to the unique file-content count, not just the unique timestamp count.
8. Open representative old workouts and verify category, timestamp, main exercises, sub entries, row times, and unilateral/bilateral flags.
9. Open statistics and verify imported workouts are included.

## Real phone migration

Only after emulator validation:

1. Keep the old `com.example.gymtrack` app installed.
2. Install the permanent-ID app `app.znypr.gymtrack` as a separate app.
3. Copy the protected export folder or ZIP contents to the phone.
4. Use the import button on the notes screen.
5. Select all 736 CSV files in one operation.
6. Record the import summary.
7. Verify the note/workout count.
8. Open several representative workouts from different years/categories.
9. Verify statistics/canonical data after import.
10. Create a new `.gymtrack-backup` from the new app after verification.

## Repeated import behavior

Bulk import skips exact duplicate records. If a record was previously imported with an adjusted timestamp because of a same-timestamp collision, a later import of the same CSV is detected by content fingerprint when the original timestamp is already occupied.

## Failure handling

The importer continues after individual file failures and reports the number of failed files plus the first failed file names. Do not remove the old app until failed files are inspected and recovered or explicitly accepted as not needed.
