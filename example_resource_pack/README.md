# Oracle Index wiki example

This is an assets-only resource pack for Minecraft 26.1.2 and Oracle Index.
It is kept as an unpacked folder so its pages are easy to edit.

## Loading it

Oracle Index reads wiki pages from client resources. Put this folder in the
profile's `resourcepacks` directory and enable it, or otherwise make it part of
the active client resources. After editing a page, use `F3 + T` to reload
resources; Oracle Index does not hot-reload wiki files.

The folder currently lives under `datapacks` as requested, but vanilla does not
load `assets/` from a data-pack repository. If no mod in the instance mirrors
global datapacks into client resources, copy or move this entire folder to
`resourcepacks` before testing.

## What to look for

Open Oracle Index and select **Oracle Example Wiki**. Its two modes contain:

- **Docs**: a normal `Welcome` documentation page.
- **Content**: pages associated with the vanilla compass, crafting table, and
  diamond pickaxe. Oracle Index should also offer these pages from the relevant
  item lookup/integration because each page declares its registry `id`.

The wiki id is `oracle-example`. All wiki files are below
`assets/oracle_index/books/oracle-example`.
