# Changes for lovely-kafka-backup

## 2025-06-06 / 1.0.0

### Breaking

- remove hardcoded S3 Region `eu-central-1`, use default providers e.g. env `AWS_REGION` or file `~/.aws/config`

## 2024-08-12 / 0.3.0

### Fix

- restore records to their original partition (previously used default partitioner)

### Feature

- upgrade to kotlin 1.9.20
- upgrade to gradle 8.9

## 2023-09-14 / 0.2.0

### Feature

- Breaking change: `readAll()` in `RecordStreamReader` returns a cold `Flow` instead of a `List`.
- bundle cli `kbackup` as command into docker image
- require `kbackup` explicitly as first argument

## 2023-09-13 / 0.1.0

- Initial release
