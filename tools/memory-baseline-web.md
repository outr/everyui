# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left | nodes made per tick | aria writes per tick |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| TextView | view | 3514 | 3416 | 2.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| TextView, ticking | view | 3827 | 3349 | 2.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| Button | button | 17064 | 8043 | 4.0 | 5.0 | 3.0 | 0.0 | 0.0 | 0.0 |
| Container | container | 3263 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| ImageView | view | 3003 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 | 0.0 | 0.0 |
| ScrollContainer | scroll | 12868 | 9884 | 4.0 | 1.0 | 3.0 | 0.01 | 0.0 | 0.0 |
| ScrollContainer of cards | card | 14589 | 15727 | 7.0 | 1.0 | 4.0 | 0.01 | 0.0 | 0.0 |
| VirtualList of 1,000 rows | list | 556737 | 243910 | 107.0 | 7.0 | 81.0 | 0.13 | 0.0 | 0.0 |
| DataTable of 100 rows | table | 965827 | 454354 | 284.0 | 154.0 | 148.0 | 0.13 | 0.0 | 0.0 |
| DataTable of 100 rows in a page | table | 979683 | 393648 | 328.0 | 175.0 | 88.0 | 0.13 | 0.0 | 0.0 |
| TabGroups | tab group | 90873 | 36645 | 25.0 | 14.0 | 20.0 | 0.05 | 0.0 | 0.0 |
| TabGroups, tabs not shown | hidden tab | 170103 | 49837 | 64.0 | 26.7 | 38.0 | 0.05 | 0.0 | 0.0 |
| Transcript, plain | message | 61507 | 20697 | 18.02 | 2.0 | 13.0 | 0.01 | 0.0 | 0.0 |
| Transcript, Markdown paragraph | message | 66132 | 32579 | 30.02 | 8.0 | 13.0 | 0.01 | 0.0 | 0.0 |
| Transcript, code block of 40 lines | message | 161424 | 429199 | 743.1 | 8.0 | 19.0 | 0.03 | 0.0 | 0.0 |
| Transcript, Markdown table | message | 220651 | 133010 | 100.1 | 5.0 | 49.0 | 0.03 | 0.0 | 0.0 |
| Transcript, tool call folded | message | 64413 | 32437 | 21.0 | 7.0 | 16.0 | 0.01 | 0.0 | 0.0 |
| Transcript, tool call open | message | 86128 | 49293 | 31.0 | 7.0 | 22.0 | 0.01 | 0.0 | 0.0 |
| Transcript, tool call running | message | 78979 | 39734 | 29.0 | 7.0 | 16.0 | 0.03 | 0.0 | 0.0 |
| Transcript in 900 px, plain | message | 6452 | 618 | 1.02 | 0.14 | 0.0 | 0.0 | 0.0 | 0.0 |
