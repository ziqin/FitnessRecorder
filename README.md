# Fitness Recorder

An Android application for reading heart rates from MI Band 2.

## Current Features

- Read realtime heart rate data from MI Band 2
- Store and export data
  - Exported database file (`*.db`) can be opened by SQLite clients like [SQLite Studio](https://sqlitestudio.pl/)

Please forgive the shortcomings in this project, which are probably caused by my lack of Android development knowledge and experience currently. Suggestions and contribution for improvements are certainly welcome.

## Remark

- Please allow the app to run in background for longtime measurement. (You may need to permit it manually, especially if you are using highly customized Android OS like MIUI, Flyme, etc.)
- It is suggested to "stop measurement" before exporting data.

## Acknowledgement

- Leo Soares, [_Mi Band 2, Part 1: Authentication_](https://leojrfs.github.io/writing/miband2-part1-auth/)
- Volodymyr Shymanskyy, [_Mi Band 2 python test_](https://github.com/vshymanskyy/miband2-python-test)
- Andrey Nikishaev, [_How I hacked my Xiaomi MiBand 2 fitness tracker—a step-by-step Linux guide_](https://medium.com/@a.nikishaev/how-i-hacked-xiaomi-miband-2-to-control-it-from-linux-a5bd2f36d3ad)
- 陈利健, [_Android BLE开发详解和FastBle源码解析_](https://www.jianshu.com/p/795bb0a08beb)
