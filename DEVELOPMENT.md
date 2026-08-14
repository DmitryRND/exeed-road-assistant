# Разработка

## Подготовка окружения

Установите JDK 17, Android SDK Platform/Build Tools, apktool 3.x и Android
Platform Tools. Пример переменных окружения:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17'
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
$env:APKTOOL_JAR = 'C:\Tools\apktool\apktool.jar'
```

`build.ps1` автоматически выбирает самую новую установленную Android platform
и версию build-tools. Исходники компилируются `javac`, преобразуются через
`d8`, ресурсы собираются apktool, после чего APK подписывается локальным
отладочным ключом.

## Запуск проверок

```powershell
powershell -ExecutionPolicy Bypass -File .\test.ps1
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Первая команда проверяет парсер, геометрию и дорожные регрессии базы камер.
Вторая собирает и проверяет подпись APK.

## Структура проекта

| Путь | Назначение |
| --- | --- |
| `src/.../MainActivity.java` | UI, VHAL, приборная панель, HUD и предупреждения |
| `src/.../SpeedCameraIndex.java` | чтение, индексирование и выбор камеры |
| `src/.../CameraLocationService.java` | фоновая геолокация |
| `apktool-src/` | манифест, ресурсы и встроенные данные APK |
| `tests/` | исполняемые регрессионные проверки |
| `build.ps1` | воспроизводимая локальная сборка |
| `install-car.ps1` | безопасная установка через ADB |

## Распределение момента

Сырые VHAL-сигналы декодируются без записи обратно в автомобиль. Штатная
формула возвращает половину процентной шкалы (`0..50` на одно колесо). Поскольку
`EstimatedCouplingTorque` описывает подключение задней муфты, назначение осей:

```java
rearPerWheel = rounded;
frontPerWheel = 50 - rounded;
```

Визуализация умножает оба значения на два. При разомкнутой муфте получается
`front=100% / rear=0%`, при росте момента увеличивается нижняя задняя ось.

## Диагностика на устройстве

```powershell
adb logcat -c
adb logcat -s InstrumentAwdProbe:I AndroidRuntime:E
```

Проверяйте интеграцию только на стоящем автомобиле. Дорожный тест выполняется
после прохождения сценария из `TESTING.md`.

## Подготовка изменения

1. Не добавляйте `.debug`, APK и каталоги `build`/`verification` в Git.
2. Выполните `test.ps1` и `build.ps1`.
3. Проверьте версию в `apktool-src/AndroidManifest.xml`.
4. Для релиза приложите APK и SHA-256 из `Get-FileHash -Algorithm SHA256`.
