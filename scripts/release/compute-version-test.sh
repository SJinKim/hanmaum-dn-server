#!/usr/bin/env sh
set -eu

unset CDPATH
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
compute_version="$script_dir/compute-version.sh"
test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT HUP INT TERM

passed=0

new_repo() {
  repo="$test_root/repo-$((passed + 1))"
  mkdir -p "$repo"
  git -C "$repo" init -q -b main
  git -C "$repo" config user.name "Release Test"
  git -C "$repo" config user.email "release-test@example.invalid"
  printf 'initial\n' >"$repo/file.txt"
  git -C "$repo" add file.txt
  git -C "$repo" commit -q -m "chore(release): initial commit"
}

commit() {
  message=$1
  printf '%s\n' "$message" >>"$repo/file.txt"
  git -C "$repo" add file.txt
  git -C "$repo" commit -q -m "$message"
}

result_value() {
  key=$1
  printf '%s\n' "$result" | sed -n "s/^${key}=//p"
}

assert_value() {
  key=$1
  expected=$2
  actual=$(result_value "$key")
  if [ "$actual" != "$expected" ]; then
    echo "Expected $key=$expected, got $key=$actual" >&2
    echo "$result" >&2
    exit 1
  fi
}

run_compute() {
  channel=$1
  result=$(cd "$repo" && sh "$compute_version" "$channel" HEAD)
}

new_repo
run_compute staging
assert_value tag "v0.1.0-st.1"
assert_value previous_tag ""
passed=$((passed + 1))

new_repo
git -C "$repo" tag v0.1.0-st.1
commit "fix(api): correct response"
run_compute staging
assert_value tag "v0.1.0-st.2"
assert_value previous_tag "v0.1.0-st.1"
passed=$((passed + 1))

new_repo
run_compute production
assert_value tag "v0.1.0"
passed=$((passed + 1))

new_repo
git -C "$repo" tag v0.1.0
commit "feat(api): add notifications"
run_compute staging
assert_value tag "v0.2.0-st.1"
run_compute production
assert_value tag "v0.2.0"
assert_value previous_tag "v0.1.0"
passed=$((passed + 1))

new_repo
git -C "$repo" tag v1.4.0
commit "fix(api): handle missing field"
run_compute production
assert_value tag "v1.4.1"
passed=$((passed + 1))

new_repo
git -C "$repo" tag v1.4.1
commit "feat(api)!: replace member response"
run_compute production
assert_value tag "v2.0.0"
passed=$((passed + 1))

new_repo
git -C "$repo" tag v1.4.1
commit "docs(cleanup): clarify deployment"
run_compute production
assert_value tag "v1.4.2"
passed=$((passed + 1))

new_repo
git -C "$repo" tag v0.1.0-st.1
run_compute staging
assert_value skip "true"
assert_value tag "v0.1.0-st.1"
passed=$((passed + 1))

# A main merge commit is not an ancestor of subsequent dev commits. Stable
# version discovery must still use the production tag as the SemVer baseline.
new_repo
git -C "$repo" switch -q -c dev
commit "fix(api): first staged change"
git -C "$repo" switch -q main
git -C "$repo" merge -q --no-ff dev -m "chore(release): promote dev"
git -C "$repo" tag v0.1.0
git -C "$repo" switch -q dev
commit "feat(api): next staged feature"
run_compute staging
assert_value tag "v0.2.0-st.1"
passed=$((passed + 1))

printf 'Passed %s release version tests.\n' "$passed"
