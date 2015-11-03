This is not an official Google product.

# Command-line "VCS" plugin for IntelliJ IDEA

Makes calls to shell scripts to get all VCS-related job done.
This allows you to write scripts to acoomodate even the strangiest
VCS workflows you may encounter.

## Command line script

Should be placed into "/usr/local/bin/cmdline-vcs" and be executable.

List of commands called from IntelliJ:

  * `cmdline-vcs get-vcs-root $directory$` is called from `$directory$` and expects
    one line output: *VCS root* corresponding to this directory. For example for *git*
    it would output path to the directory where `.git` is located.
  * `cmdline-vcs list-changed-files $directory$` is called from `$directory$` associated
    with VCS root. Expects multi-line output, one file per line in the next format:
    `$filename$;revision$` Where `$revision$` is a revision of a file you'll see a diff
    in IntelliJ against. It should uniquely identify file contents and is used for caching.
    One special value is `NULL` which is used for files that were just added and are
    not yet present in the VCS. For example for *git* you can use file's checksum or currently
    checked out revision.
  * `cmdline-vcs get-base-file-contents $filename$ $revision$` is called from a directory
    of this file and expects file contents as an output. File ane revision used were
    previously returned by `list-changed-files`. Result is cached.