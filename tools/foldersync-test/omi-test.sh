#!/data/data/com.termux/files/usr/bin/bash
# OMI step 0: FolderSync test. Run in Termux on BOTH phones.
# Phone 1 uses the letter A, phone 2 uses the letter B.
#
#   bash omi-test.sh A setup      create the test folder
#   bash omi-test.sh A write      write 3 event files and 1 delete-me file
#   bash omi-test.sh A conflict   write conflict-test.json (run on both phones while offline)
#   bash omi-test.sh A delete     delete this phone's delete-me file
#   bash omi-test.sh A check      print the report to paste back
#
# Only touches Documents/OMI-test. Delete that folder when the test is finished.

DEV="${1:-}"
STEP="${2:-}"

case "$DEV" in
  A) OTHER=B ;;
  B) OTHER=A ;;
  *) echo "First argument must be A or B, for example: bash omi-test.sh A setup"; exit 1 ;;
esac

ROOT="$HOME/storage/shared"
DIR="$ROOT/Documents/OMI-test"
EVENTS="$DIR/events"

if [ ! -d "$ROOT" ]; then
  echo "No storage access yet. Run: termux-setup-storage"
  echo "Allow the permission, then run this again."
  exit 1
fi

now() { date +%Y-%m-%dT%H:%M:%S%z; }

do_setup() {
  mkdir -p "$EVENTS"
  echo "Created $DIR"
  echo "Next: in FolderSync add a pair, local Documents/OMI-test <-> OneDrive /OMI-test, two-way."
}

do_write() {
  mkdir -p "$EVENTS"
  for i in 1 2 3; do
    name="$(date +%Y-%m-%dT%H-%M-%S)_${DEV}_${i}.json"
    printf '{"phone":"%s","n":%s,"written":"%s"}\n' "$DEV" "$i" "$(now)" > "$EVENTS/$name.tmp"
    mv "$EVENTS/$name.tmp" "$EVENTS/$name"
    sleep 1
  done
  printf '{"phone":"%s","written":"%s"}\n' "$DEV" "$(now)" > "$DIR/delete-me-$DEV.json"
  echo "Wrote 3 event files and delete-me-$DEV.json at $(now)."
  echo "Wait about 10 minutes, then run: bash omi-test.sh $DEV check"
}

do_conflict() {
  mkdir -p "$DIR"
  printf '{"phone":"%s","written":"%s"}\n' "$DEV" "$(now)" > "$DIR/conflict-test.json"
  echo "Wrote conflict-test.json as phone $DEV at $(now)."
  echo "Run this on both phones while both are in airplane mode, then turn airplane mode off."
}

do_delete() {
  rm -f "$DIR/delete-me-$DEV.json"
  echo "Deleted delete-me-$DEV.json at $(now)."
  echo "Wait about 10 minutes, then run check on the other phone."
}

do_check() {
  if [ ! -d "$DIR" ]; then
    echo "FAIL: $DIR does not exist. Run the setup step first."
    exit 1
  fi

  echo "===== OMI sync report ====="
  echo "phone: $DEV   time: $(now)   android: $(getprop ro.build.version.release 2>/dev/null)"
  echo

  mine=$(ls "$EVENTS" 2>/dev/null | grep -c "_${DEV}_")
  theirs=$(ls "$EVENTS" 2>/dev/null | grep -c "_${OTHER}_")
  echo "1. Event files"
  echo "   written by this phone ($DEV): $mine of 3"
  echo "   arrived from phone $OTHER: $theirs of 3"
  if [ "$theirs" -eq 3 ]; then
    echo "   RESULT: PASS"
  else
    echo "   RESULT: NOT YET (wait a few minutes and run check again)"
  fi
  echo

  echo "2. Conflict test"
  cfiles=$(ls "$DIR" 2>/dev/null | grep '^conflict-test')
  cn=$(printf '%s\n' "$cfiles" | grep -c .)
  echo "   conflict-test files here: $cn"
  for f in "$DIR"/conflict-test*; do
    if [ -e "$f" ]; then
      echo "   $(basename "$f"): $(cat "$f")"
    fi
  done
  if [ "$cn" -eq 0 ]; then
    echo "   RESULT: not run yet"
  elif [ "$cn" -eq 1 ]; then
    echo "   RESULT: one file, the last writer won"
  else
    echo "   RESULT: $cn files, a conflict copy was kept"
  fi
  echo

  echo "3. Delete test"
  if [ -e "$DIR/delete-me-$OTHER.json" ]; then
    echo "   delete-me-$OTHER.json: present"
  else
    echo "   delete-me-$OTHER.json: not here (never arrived, or already deleted)"
  fi
  echo

  echo "4. Leftover .tmp files: $(find "$DIR" -name '*.tmp' | wc -l)"
  echo

  echo "5. All files here (modified, size in bytes, path)"
  find "$DIR" -type f -printf '   %TY-%Tm-%Td %TT  %s  %P\n' | sort
  echo "===== end ====="
}

case "$STEP" in
  setup)    do_setup ;;
  write)    do_write ;;
  conflict) do_conflict ;;
  delete)   do_delete ;;
  check)    do_check ;;
  *)
    echo "Second argument must be one of: setup, write, conflict, delete, check"
    exit 1
    ;;
esac
