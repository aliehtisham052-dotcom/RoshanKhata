# Hudhud baseline at b86f257 (25 Nov 2026, Marhala 0)

The master plan v1 baseline (`docs/hudhud-master-plan-v1.md`, Marhala 0), on branch kmp-desktop-16 at commit b86f257, run
with `scripts/hudhud_baseline.sh --no-seeds` (the seed noise was measured on 24 Nov: `docs/hudhud-baseline-24nov2026.md`,
about +-12 rows on the owner lists, +-6 on held-out). Speed is on a 2-core Linux runner, not a phone. These are the floors
every later phase starts from; all owner lists and held-out are dev data now, not "accuracy".

## Hashes (`scripts/hudhud_baseline.sh --hashes`)

```
d79eab4bc30d9a5338dc44404001bf8be6ce5ca8500c78b11fcad9189c136b5a app/src/main/assets/hudhud/sense.bin
e8ce2d4d586e624ba83e68ec31fa7e4cb1a09c607f42ea438eb5aeb5a3216eb2 shared/hudhud/ar.tsv
f90ac30df1a9ca0b7d4e10f44139321632f40e688ef7468a9584fe806fa353c2 shared/hudhud/bn.tsv
2f9894c7c840855ea16aa63dcf45a964763eea69ad92031eca2135a4cc459e90 shared/hudhud/en.tsv
3215e2c7baff633a8130ad8d12d7ad7aaa5270ebc2d7b640b26e86b6b422ba3b shared/hudhud/fa.tsv
72b5025b412df17ffc90282ea088c4a870cd7798d7d9ac8e045e5c1e05dff688 shared/hudhud/hi.tsv
f3fc5015f9f7817ece6820259f79da7f9107daf01a0632e7f80fcc79bf4afa31 shared/hudhud/id.tsv
1f8313c8d1ed1f8388fcd32a58533a828fa3917d8c3df9b2397cff375a4d542f shared/hudhud/sd.tsv
108e595495f2671ab912f7e17ae8e1b46cc67bee4aec28c66a8e17747290c14a shared/hudhud/ur-Latn.tsv
740dfc5e041014cc9f1f93f653cf3c9a19d6264471ab998077ad9082c4985695 shared/hudhud/ur.tsv
983d5a40138a15ac995929c107561751ee40e3af33384cbb01ad033f07ecb25c shared/hudhud/more/ar.tsv
a9fd90558b76a72239377c45d5db6fae6ccb52d4ff7a4e404353f194b0544b90 shared/hudhud/more/bn.tsv
bfa90e842872102a4a20696ca5d4ce8652b2aa0fe16c3db47d04087f99283d39 shared/hudhud/more/en.tsv
4b19e8633e7368652c75dc2f166cb749be5de747ebfdd708f09b01b862f3418b shared/hudhud/more/fa.tsv
ee640ce54693f187a640f60331dffc3cbf2ba4cc54ee7ca713d2e9a919c76f92 shared/hudhud/more/hi.tsv
fad973ed763c7dd30723d120e0c72404d80e69df4b27c4b02a37ce1933dc5642 shared/hudhud/more/id.tsv
1f34f43b9ef81bb3e31ef37ce880975f5f91d3e70a370f5b219b9e63eb095f20 shared/hudhud/more/sd.tsv
8c35a697677bf859836b8bdff1b6a5af04266b99b3befa4aea3e6016c642e869 shared/hudhud/more/ur-Latn.tsv
ad6ec8a48b687a7cd5f39bba648db4c6c750803a62cc8cb51061ac91d1f35dad shared/hudhud/more/ur.tsv
3c297443929b5e48cf9d3e2ee18338f3b63a8219d98326903ea982c401634684 shared/src/jvmSharedTest/resources/hudhud/bank.tsv
907a9e7f51a54c14b2b2ab69b2db44bf938ff4d67a0c1813ed3f49fbe74a7dc8 shared/src/jvmSharedTest/resources/hudhud/heldout.tsv
6aa61d2bb022f8bd118ab2ae831a43284fe4b84354769d94ec8bc1bb767d79af shared/src/jvmSharedTest/resources/hudhud/owner_1000.tsv
3862e89bb2a908b8b434f208ebcc219a8caab0179fc15b73ca53c3ac03520043 shared/src/jvmSharedTest/resources/hudhud/owner_set.tsv
b9ed16367517cfce9dd188cb41ea71a7a595047f5d26709a61b25be0bce5a27b shared/src/jvmSharedTest/resources/hudhud/pages.tsv
9258ab216b960c8452fe7828b17589ddf5045e45e07b0dd3aac9d81f6a82a2ef shared/src/jvmSharedTest/resources/hudhud/review_d.tsv
162175804bba3b792fceb87ccd3319b24e4970603f5e262b847630160630ca1a shared/src/jvmSharedTest/resources/hudhud/review_o.tsv
```

## Figures

Run: 2026-11-25T20:13:35.251418900; bank 13374 lines; sense.bin sha256 d79eab4bc30d9a5338dc44404001bf8be6ce5ca8500c78b11fcad9189c136b5a
Load average at start: 1.04 0.26 0.09 3/124 433; processors 2

### Accuracy per set and language (right/total, shipped sense, seed 313)

| set | en | ur-Latn | ur | sd | ar | fa | hi | bn | id | ALL |
|---|---|---|---|---|---|---|---|---|---|---|
| heldout strict | 123/130 94.6% | 120/130 92.3% | 122/130 93.8% | 120/130 92.3% | 117/130 90.0% | 117/130 90.0% | 121/130 93.1% | 120/130 92.3% | 121/130 93.1% | 1081/1170 92.4% |
| heldout lenient | 124/130 95.4% | 122/130 93.8% | 124/130 95.4% | 121/130 93.1% | 119/130 91.5% | 119/130 91.5% | 123/130 94.6% | 122/130 93.8% | 123/130 94.6% | 1097/1170 93.8% |
| owner_set | - | 159/200 79.5% | - | - | - | - | - | - | - | 159/200 79.5% |
| owner_1000 | - | 812/1000 81.2% | - | - | - | - | - | - | - | 812/1000 81.2% |
| review_d | 127/130 97.7% | 299/322 92.9% | 103/108 95.4% | 79/86 91.9% | 87/94 92.6% | 85/94 90.4% | 97/101 96.0% | 84/90 93.3% | 85/90 94.4% | 1046/1115 93.8% |
| review_o | 143/153 93.5% | 292/308 94.8% | 130/161 80.7% | 69/69 100.0% | 69/74 93.2% | 71/72 98.6% | 123/145 84.8% | 78/78 100.0% | 99/107 92.5% | 1074/1167 92.0% |

- review_d: 1115 rows; 1046 judged OK by the reviewer, 1046 of those still read exactly as held; the other 69 are MISS/GUESS/NOW rows (counted as not right here, even if the reading changed).
- review_o: 1167 rows; 1074 judged OK by the reviewer, 1074 of those still read exactly as held; the other 93 are MISS/GUESS/NOW rows (counted as not right here, even if the reading changed).

### Entity metrics (correct/wrong/choice/missing/otherwise; - = not labelled)

| set | side | amount | person | product | days |
|---|---|---|---|---|---|
| heldout | 122/0/0/4/0 | 36/0/0/0/0 | 162/0/0/0/0 | 90/0/0/0/0 | - |
| owner_set | 37/1/0/5/6 | - | 13/0/0/0/0 | - | - |
| owner_1000 | 125/3/0/29/63 | - | 58/0/0/0/0 | - | 25/0/0/4/11 |
| review_d | 207/0/0/0/0 | 19/0/0/0/0 | 167/0/0/0/0 | 83/0/0/4/0 | 4/0/0/0/0 |
| review_o | 122/0/0/0/0 | 7/0/0/0/0 | 130/0/0/0/16 | 100/0/0/26/0 | 3/0/0/0/0 |

Per language, all sets pooled (correct/wrong/choice/missing/otherwise):

| lang | side | amount | person | product | days |
|---|---|---|---|---|---|
| en | 48/0/0/0/0 | 9/0/0/0/0 | 63/0/0/0/2 | 44/0/0/0/0 | 2/0/0/0/0 |
| ur-Latn | 102/0/0/0/0 | 11/0/0/0/0 | 115/0/0/0/2 | 80/0/0/0/0 | 4/0/0/0/0 |
| ur | 56/0/0/0/0 | 5/0/0/0/0 | 45/0/0/0/2 | 16/0/0/17/0 | 1/0/0/0/0 |
| sd | 39/0/0/0/0 | 6/0/0/0/0 | 36/0/0/0/1 | 21/0/0/0/0 | - |
| ar | 36/0/0/1/0 | 7/0/0/0/0 | 35/0/0/0/1 | 23/0/0/0/0 | - |
| fa | 37/0/0/2/0 | 6/0/0/0/0 | 37/0/0/0/2 | 23/0/0/0/0 | - |
| hi | 49/0/0/0/0 | 6/0/0/0/0 | 43/0/0/0/2 | 16/0/0/13/0 | - |
| bn | 43/0/0/0/0 | 6/0/0/0/0 | 35/0/0/0/2 | 23/0/0/0/0 | - |
| id | 41/0/0/1/0 | 6/0/0/0/0 | 50/0/0/0/2 | 27/0/0/0/0 | - |
| owner | 162/4/0/34/69 | - | 71/0/0/0/0 | - | 25/0/0/4/11 |

### Confusions, top 20 (owner lists + review OK rows: expected reading -> read; held-out in its own table)

| n | expected | read |
|---|---|---|
| 8 | Help:screen_seasons\|Activity\|Figure:WHO_OWES_MOST\|Help:screen_khata | Figure:TO_GET |
| 3 | BackupStatus\|Help:faq_backup\|Help:faq_restore\|Help:intro_backup\|About:PRIVACY\|Help:faq_snapshot | Returns |
| 3 | Figure:EXPIRING\|Help:intro_expiry\|Goods | Status:LOW_STOCK |
| 3 | Figure:SALES\|Period:SALES\|Help:screen_sales_report\|Help:screen_insights | SpanLimit |
| 3 | Help:screen_seasons\|Collected\|Activity\|Figure:NOT_PAID\|WhenPays\|NeedName:LAST_PAYMENT\|NeedName:PROMISE\|Help:intro_radar | SpanLimit |
| 3 | Voice\|Chat\|Abilities\|Help\|About:APP | About:OWNER_NAME |
| 2 | Cashbook\|Help:intro_cashbook\|Status:STAFF | Figure:WHO_OWES_MOST |
| 2 | Figure:PROFIT_MONTH\|Period:PROFIT\|Help:screen_insights\|Cashbook\|TopGoods | Figure:WHO_OWES_MOST |
| 2 | Figure:SALES\|Period:SALES\|Help:screen_sales_report\|Activity\|Collected\|Figure:TO_GET\|Today | Drawer |
| 2 | Figure:SALES\|Period:SALES\|Help:screen_sales_report\|Help:screen_insights | Help:screen_seasons |
| 2 | Figure:TO_GIVE\|Status:BILLS | Figure:NOT_PAID |
| 2 | Figure:TO_GIVE\|Status:BILLS | Figure:TO_GET |
| 2 | Help:intro_documents\|Count:INVOICES | Count:BILLS:null |
| 2 | Help:screen_cashflow | Figure:TO_GIVE |
| 2 | Help:screen_insights\|Help:screen_sales_report\|TopGoods\|Help:screen_lapsed | Status:LOW_STOCK |
| 2 | Help\|Today\|Activity\|Figure\|Period\|Status:BOOK | SpanLimit |
| 2 | NeedGoods\|Goods:BUYERS\|NeedName:BUYERS\|NeedName | Figure:TO_GET |
| 2 | NeedGoods\|Goods\|Help:screen_rates\|Help:screen_purchase_rates\|Help:screen_products\|NeedName | Help:intro_orders |
| 2 | NeedGoods\|Goods\|Help:screen_rates\|Help:screen_purchase_rates\|Help:screen_products\|NeedName | Status:LOW_STOCK |
| 2 | Today\|Activity\|Count:ENTRIES:TODAY\|Cashbook | Status:BOOK |
(distinct pairs: 197; misses pooled: 229)

Held-out, label -> label read, top 12:

| n | expected | read |
|---|---|---|
| 13 | help_howto | abilities |
| 3 | bye | (none) |
| 3 | can_talk | praise |
| 3 | goods_stock | goods_price |
| 2 | abilities | who_are_you |
| 2 | app_about | maker |
| 2 | bills_today | (none) |
| 2 | can_talk | who_are_you |
| 2 | collected | (none) |
| 2 | likely_payers | collected |
| 2 | maker | who_are_you |
| 2 | returns_today | activity |
(distinct pairs: 62; strict misses: 89)

### Reading speed (ms per Hudhud.understand call, shipped sense, after a warm-up of 600 questions)

| set | n | median | p95 | max |
|---|---|---|---|---|
| heldout | 1170 | 5.43 | 10.66 | 31.0 |
| owner_set | 200 | 5.18 | 14.56 | 120.8 |
| owner_1000 | 1000 | 5.17 | 10.31 | 22.2 |
| review_d | 1115 | 4.21 | 8.44 | 25.3 |
| review_o | 1167 | 4.73 | 9.46 | 33.6 |
| ALL | 4652 | 4.93 | 9.92 | 120.8 |

