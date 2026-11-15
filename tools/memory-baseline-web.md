# EveryUI's memory on the web, per unit of each case (MemoryCheck; headless Chrome)

| case | unit | JS heap bytes | DOM heap bytes | DOM nodes | listeners | components | components left |
|---|---|---:|---:|---:|---:|---:|---:|
| TextView | view | 9081 | 3774 | 2.0 | 0.0 | 1.0 | 0.0 |
| Button | button | 30433 | 8403 | 4.0 | 5.0 | 3.0 | 0.0 |
| Container | container | 6048 | 2249 | 1.0 | 0.0 | 1.0 | 0.0 |
| ImageView | view | 5384 | 2563 | 2.0 | 0.0 | 1.0 | 0.01 |
| ScrollContainer | scroll | 23192 | 10326 | 4.0 | 1.0 | 3.0 | 0.01 |
| VirtualList of 1,000 rows | list | 1497516 | 387680 | 185.0 | 7.0 | 159.0 | 0.13 |
| DataTable of 100 rows | table | 6235795 | 1865820 | 1153.0 | 549.0 | 634.0 | 0.13 |
| TabGroups | tab group | 153534 | 38026 | 25.0 | 14.0 | 20.0 | 0.05 |
| TabGroups, tabs not shown | hidden tab | 351641 | 56686 | 64.0 | 26.0 | 38.0 | 0.05 |
| Transcript, plain | message | 103609 | 21774 | 17.02 | 2.0 | 13.0 | 0.01 |
| Transcript, Markdown paragraph | message | 109039 | 31596 | 29.02 | 8.0 | 13.0 | 0.01 |
| Transcript, code block of 40 lines | message | 225980 | 420135 | 743.1 | 8.0 | 19.0 | 0.03 |
| Transcript, Markdown table | message | 389525 | 136762 | 99.1 | 5.0 | 49.0 | 0.03 |
| Transcript, tool call folded | message | 117270 | 33862 | 21.0 | 7.0 | 16.0 | 0.01 |
| Transcript, tool call open | message | 167036 | 52731 | 31.0 | 7.0 | 22.0 | 0.01 |
| Transcript in 900 px, plain | message | 9435 | 2778 | 1.02 | 0.14 | 0.0 | 0.0 |
