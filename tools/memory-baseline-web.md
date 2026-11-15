# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left |
|---|---|---:|---:|---:|---:|---:|---:|
| TextView | view | 9095 | 3774 | 2.0 | 0.0 | 1.0 | 0.0 |
| Button | button | 30481 | 8403 | 4.0 | 5.0 | 3.0 | 0.0 |
| Container | container | 6057 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 |
| ImageView | view | 5481 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 |
| ScrollContainer | scroll | 23680 | 10326 | 4.0 | 1.0 | 3.0 | 0.01 |
| ScrollContainer of cards | card | 33928 | 16806 | 7.0 | 1.0 | 4.0 | 0.01 |
| VirtualList of 1,000 rows | list | 860431 | 253164 | 107.0 | 7.0 | 81.0 | 0.13 |
| DataTable of 100 rows | table | 1647119 | 501914 | 284.0 | 154.0 | 148.0 | 0.13 |
| DataTable of 100 rows in a page | table | 1808120 | 449528 | 328.0 | 175.0 | 88.0 | 0.13 |
| TabGroups | tab group | 154248 | 38445 | 25.0 | 14.0 | 20.0 | 0.05 |
| TabGroups, tabs not shown | hidden tab | 356988 | 57426 | 64.0 | 26.7 | 38.0 | 0.05 |
| Transcript, plain | message | 104182 | 27386 | 17.02 | 2.0 | 13.0 | 0.01 |
| Transcript, Markdown paragraph | message | 109608 | 31930 | 29.02 | 8.0 | 13.0 | 0.01 |
| Transcript, code block of 40 lines | message | 228259 | 424580 | 743.1 | 8.0 | 19.0 | 0.03 |
| Transcript, Markdown table | message | 391828 | 163816 | 99.1 | 5.0 | 49.0 | 0.03 |
| Transcript, tool call folded | message | 118052 | 40995 | 21.0 | 7.0 | 16.0 | 0.01 |
| Transcript, tool call open | message | 167875 | 51393 | 31.0 | 7.0 | 22.0 | 0.01 |
| Transcript in 900 px, plain | message | 9504 | 620 | 1.02 | 0.14 | 0.0 | 0.0 |
