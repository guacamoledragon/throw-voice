#!/bin/sh
# Builds public/ from src/site/. Each top-level page also gets its own HTML file with the
# rendered markdown inside #app, so crawlers that skip JavaScript can read it. Docsify
# replaces that content when it starts.
set -eu

src=src/site
out=public
site=https://pawa.im

rm -rf "$out"
cp -r "$src" "$out"

urls=""
for md in "$src"/*.md; do
  name=$(basename "$md" .md)
  case "$name" in
    _*) continue ;;
    README) page=index url="$site/" ;;
    *) page=$name url="$site/$name.html" ;;
  esac

  body=$(mktemp)
  npx --yes marked@18.1.0 --gfm -i "$md" |
    sed 's|href="\([a-z0-9-]*\)\.md"|href="\1.html"|g' > "$body"

  title=$(awk '/^# /{sub(/^# /, ""); print; exit}' "$md")
  case "$title" in *pawa*) ;; *) title="$title - pawa" ;; esac

  # ponytail: per-page title and homepage only; the meta description is site-wide
  awk -v body="$body" -v name="$name" -v title="$title" -v url="$url" '
    /<div id="app"><\/div>/ {
      print "  <div id=\"app\" class=\"markdown-section\">"
      while ((getline line < body) > 0) print line
      print "  </div>"
      next
    }
    name != "README" && /^  <title>/ { print "  <title>" title "</title>"; next }
    name != "README" && /loadSidebar: true,/ { print "      homepage: \"" name ".md\"," }
    /<\/head>/ { print "  <link rel=\"canonical\" href=\"" url "\">" }
    { print }
  ' "$src/index.html" > "$out/$page.html"
  rm "$body"

  urls="$urls  <url><loc>$url</loc></url>
"
done

printf '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n%s</urlset>\n' \
  "$urls" > "$out/sitemap.xml"
