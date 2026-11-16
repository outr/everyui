# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left | nodes made per tick | aria writes per tick |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| TextView | view | 3512 | 3414 | 2.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| TextView, ticking | view | 3794 | 3349 | 2.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| Button | button | 17071 | 8043 | 4.0 | 5.0 | 3.0 | 0.0 | 0.0 | 0.0 |
| Container | container | 3249 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 |
| ImageView | view | 2967 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 | 0.0 | 0.0 |
| ScrollContainer | scroll | 12870 | 9884 | 4.0 | 1.0 | 3.0 | 0.01 | 0.0 | 0.0 |
| ScrollContainer of cards | card | 14570 | 15727 | 7.0 | 1.0 | 4.0 | 0.01 | 0.0 | 0.0 |
| VirtualList of 1,000 rows | list | 556486 | 243804 | 107.0 | 7.0 | 81.0 | 0.13 | 0.0 | 0.0 |
| DataTable of 100 rows | table | 963431 | 454354 | 284.0 | 154.0 | 148.0 | 0.13 | 0.0 | 0.0 |
| DataTable of 100 rows in a page | table | 976877 | 393648 | 328.0 | 175.0 | 88.0 | 0.13 | 0.0 | 0.0 |
| TabGroups | tab group | 90704 | 36645 | 25.0 | 14.0 | 20.0 | 0.05 | 0.0 | 0.0 |
| TabGroups, tabs not shown | hidden tab | 169886 | 49794 | 64.0 | 26.7 | 38.0 | 0.05 | 0.0 | 0.0 |
| Transcript, plain | message | 61318 | 20697 | 18.02 | 2.0 | 13.0 | 0.01 | 0.0 | 0.0 |
| Transcript, Markdown paragraph | message | 65905 | 32579 | 30.02 | 8.0 | 13.0 | 0.01 | 0.0 | 0.0 |
| Transcript, code block of 40 lines | message | 161981 | 429200 | 743.1 | 8.0 | 19.0 | 0.03 | 0.0 | 0.0 |
| Transcript, Markdown table | message | 220469 | 133010 | 100.1 | 5.0 | 49.0 | 0.03 | 0.0 | 0.0 |
| Transcript, tool call folded | message | 64426 | 32287 | 21.0 | 7.0 | 16.0 | 0.01 | 0.0 | 0.0 |
| Transcript, tool call open | message | 86215 | 49130 | 31.0 | 7.0 | 22.0 | 0.01 | 0.0 | 0.0 |
| Transcript, tool call running | message | 78433 | 39354 | 29.0 | 7.0 | 16.0 | 0.03 | 0.0 | 0.0 |
| Transcript in 900 px, plain | message | 6428 | 618 | 1.02 | 0.14 | 0.0 | 0.0 | 0.0 | 0.0 |
