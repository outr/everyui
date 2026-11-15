# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left |
|---|---|---:|---:|---:|---:|---:|---:|
| TextView | view | 9099 | 3774 | 2.0 | 0.0 | 1.0 | 0.0 |
| Button | button | 30470 | 8403 | 4.0 | 5.0 | 3.0 | 0.0 |
| Container | container | 6067 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 |
| ImageView | view | 5467 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 |
| ScrollContainer | scroll | 23687 | 10326 | 4.0 | 1.0 | 3.0 | 0.01 |
| ScrollContainer of cards | card | 33911 | 16806 | 7.0 | 1.0 | 4.0 | 0.01 |
| VirtualList of 1,000 rows | list | 860358 | 253208 | 107.0 | 7.0 | 81.0 | 0.13 |
| DataTable of 100 rows | table | 1641193 | 501914 | 284.0 | 154.0 | 148.0 | 0.13 |
| DataTable of 100 rows in a page | table | 1808775 | 449528 | 328.0 | 175.0 | 88.0 | 0.13 |
| TabGroups | tab group | 154368 | 38445 | 25.0 | 14.0 | 20.0 | 0.05 |
| TabGroups, tabs not shown | hidden tab | 356734 | 57469 | 64.0 | 26.7 | 38.0 | 0.05 |
| Transcript, plain | message | 109675 | 22249 | 18.02 | 2.0 | 13.0 | 0.01 |
| Transcript, Markdown paragraph | message | 115116 | 34131 | 30.02 | 8.0 | 13.0 | 0.01 |
| Transcript, code block of 40 lines | message | 228572 | 431470 | 743.1 | 8.0 | 19.0 | 0.03 |
| Transcript, Markdown table | message | 397981 | 144130 | 100.1 | 5.0 | 49.0 | 0.03 |
| Transcript, tool call folded | message | 117927 | 35052 | 21.0 | 7.0 | 16.0 | 0.01 |
| Transcript, tool call open | message | 167714 | 53288 | 31.0 | 7.0 | 22.0 | 0.01 |
| Transcript in 900 px, plain | message | 9617 | 619 | 1.02 | 0.14 | 0.0 | 0.0 |
