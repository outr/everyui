# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left |
|---|---|---:|---:|---:|---:|---:|---:|
| TextView | view | 3495 | 3358 | 2.0 | 0.0 | 1.0 | 0.0 |
| Button | button | 16980 | 7987 | 4.0 | 5.0 | 3.0 | 0.0 |
| Container | container | 3220 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 |
| ImageView | view | 3024 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 |
| ScrollContainer | scroll | 12796 | 9910 | 4.0 | 1.0 | 3.0 | 0.01 |
| ScrollContainer of cards | card | 14453 | 15559 | 7.0 | 1.0 | 4.0 | 0.01 |
| VirtualList of 1,000 rows | list | 554553 | 242348 | 107.0 | 7.0 | 81.0 | 0.13 |
| DataTable of 100 rows | table | 959462 | 454074 | 284.0 | 154.0 | 148.0 | 0.13 |
| DataTable of 100 rows in a page | table | 973120 | 393368 | 328.0 | 175.0 | 88.0 | 0.13 |
| TabGroups | tab group | 90212 | 36365 | 25.0 | 14.0 | 20.0 | 0.05 |
| TabGroups, tabs not shown | hidden tab | 168158 | 46610 | 64.0 | 26.7 | 38.0 | 0.05 |
| Transcript, plain | message | 60702 | 20585 | 18.02 | 2.0 | 13.0 | 0.01 |
| Transcript, Markdown paragraph | message | 65706 | 32467 | 30.02 | 8.0 | 13.0 | 0.01 |
| Transcript, code block of 40 lines | message | 160715 | 428976 | 743.1 | 8.0 | 19.0 | 0.03 |
| Transcript, Markdown table | message | 218770 | 132898 | 100.1 | 5.0 | 49.0 | 0.03 |
| Transcript, tool call folded | message | 63743 | 32120 | 21.0 | 7.0 | 16.0 | 0.01 |
| Transcript, tool call open | message | 85258 | 48738 | 31.0 | 7.0 | 22.0 | 0.01 |
| Transcript in 900 px, plain | message | 6421 | 619 | 1.02 | 0.14 | 0.0 | 0.0 |
