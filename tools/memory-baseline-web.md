# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left |
|---|---|---:|---:|---:|---:|---:|---:|
| TextView | view | 4147 | 3360 | 2.0 | 0.0 | 1.0 | 0.0 |
| Button | button | 21893 | 7987 | 4.0 | 5.0 | 3.0 | 0.0 |
| Container | container | 3941 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 |
| ImageView | view | 3795 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 |
| ScrollContainer | scroll | 15674 | 9919 | 4.0 | 1.0 | 3.0 | 0.01 |
| ScrollContainer of cards | card | 17483 | 15559 | 7.0 | 1.0 | 4.0 | 0.01 |
| VirtualList of 1,000 rows | list | 673844 | 242348 | 107.0 | 7.0 | 81.0 | 0.13 |
| DataTable of 100 rows | table | 1185925 | 454074 | 284.0 | 154.0 | 148.0 | 0.13 |
| DataTable of 100 rows in a page | table | 1263024 | 393368 | 328.0 | 175.0 | 88.0 | 0.13 |
| TabGroups | tab group | 109475 | 36365 | 25.0 | 14.0 | 20.0 | 0.05 |
| TabGroups, tabs not shown | hidden tab | 210198 | 46610 | 64.0 | 26.7 | 38.0 | 0.05 |
| Transcript, plain | message | 76443 | 20585 | 18.02 | 2.0 | 13.0 | 0.01 |
| Transcript, Markdown paragraph | message | 81605 | 32467 | 30.02 | 8.0 | 13.0 | 0.01 |
| Transcript, code block of 40 lines | message | 183144 | 428975 | 743.1 | 8.0 | 19.0 | 0.03 |
| Transcript, Markdown table | message | 276612 | 132898 | 100.1 | 5.0 | 49.0 | 0.03 |
| Transcript, tool call folded | message | 76781 | 32143 | 21.0 | 7.0 | 16.0 | 0.01 |
| Transcript, tool call open | message | 106010 | 48721 | 31.0 | 7.0 | 22.0 | 0.01 |
| Transcript in 900 px, plain | message | 8053 | 619 | 1.02 | 0.14 | 0.0 | 0.0 |
