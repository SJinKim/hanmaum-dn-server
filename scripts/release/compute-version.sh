#!/usr/bin/env sh
set -eu

channel="${1:-}"
target="${2:-HEAD}"

case "$channel" in
  staging | production) ;;
  *)
    echo "Usage: $0 <staging|production> [target-commit]" >&2
    exit 2
    ;;
esac

git rev-parse --verify "${target}^{commit}" >/dev/null
target_sha=$(git rev-parse "${target}^{commit}")

stable_tag_pattern='^v[0-9]+\.[0-9]+\.[0-9]+$'
staging_tag_pattern='^v[0-9]+\.[0-9]+\.[0-9]+-st\.[1-9][0-9]*$'

latest_matching_tag() {
  pattern=$1
  merged_target=${2:-}

  if [ -n "$merged_target" ]; then
    git tag --merged "$merged_target" --sort=-version:refname
  else
    git tag --list --sort=-version:refname
  fi | awk -v pattern="$pattern" '$0 ~ pattern { print; exit }'
}

tag_at_target() {
  pattern=$1
  git tag --points-at "$target_sha" --sort=-version:refname |
    awk -v pattern="$pattern" '$0 ~ pattern { print; exit }'
}

if [ "$channel" = "staging" ]; then
  existing_tag=$(tag_at_target "$staging_tag_pattern")
else
  existing_tag=$(tag_at_target "$stable_tag_pattern")
fi

if [ -n "$existing_tag" ]; then
  printf 'skip=true\n'
  printf 'tag=%s\n' "$existing_tag"
  printf 'version=%s\n' "${existing_tag#v}"
  printf 'previous_tag=\n'
  exit 0
fi

# Production tags live on main merge commits and are therefore not necessarily
# ancestors of dev. Select the highest stable SemVer tag repository-wide, then
# let git calculate commits reachable from the target but not from that tag.
previous_stable_tag=$(latest_matching_tag "$stable_tag_pattern")

if [ -z "$previous_stable_tag" ]; then
  next_core_version="0.1.0"
else
  stable_version=${previous_stable_tag#v}
  major=${stable_version%%.*}
  remainder=${stable_version#*.}
  minor=${remainder%%.*}
  patch=${remainder#*.}

  commit_messages=$(mktemp)
  trap 'rm -f "$commit_messages"' EXIT HUP INT TERM
  git log --format='%s%n%b' "${previous_stable_tag}..${target_sha}" >"$commit_messages"

  if grep -Eq '^[[:alnum:]-]+(\([^)]*\))?!:|^BREAKING([ -]CHANGE)?:[[:space:]]' "$commit_messages"; then
    major=$((major + 1))
    minor=0
    patch=0
  elif grep -Eq '^feat(\([^)]*\))?:' "$commit_messages"; then
    minor=$((minor + 1))
    patch=0
  else
    # fix/perf commits are SemVer patches. A patch fallback also keeps releases
    # unique for deployable internal changes that do not affect the public API.
    patch=$((patch + 1))
  fi

  next_core_version="${major}.${minor}.${patch}"
fi

if [ "$channel" = "staging" ]; then
  previous_tag=$(latest_matching_tag "$staging_tag_pattern" "$target_sha")
  escaped_core_version=$(printf '%s\n' "$next_core_version" | sed 's/\./\\./g')
  latest_same_version=$(
    git tag --merged "$target_sha" --list "v${next_core_version}-st.*" --sort=-version:refname |
      awk -v pattern="^v${escaped_core_version}-st\\.[1-9][0-9]*$" \
        '$0 ~ pattern { print; exit }'
  )

  if [ -n "$latest_same_version" ]; then
    sequence=${latest_same_version##*.}
    sequence=$((sequence + 1))
  else
    sequence=1
  fi

  version="${next_core_version}-st.${sequence}"
  tag="v${version}"
else
  previous_tag=$previous_stable_tag
  version=$next_core_version
  tag="v${version}"
fi

printf 'skip=false\n'
printf 'tag=%s\n' "$tag"
printf 'version=%s\n' "$version"
printf 'previous_tag=%s\n' "$previous_tag"
