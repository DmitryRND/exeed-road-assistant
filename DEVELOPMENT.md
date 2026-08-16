# Разработка

## Подготовка окружения

Установите JDK 21, Android SDK Platform/Build Tools и Android Platform Tools.
MapKit 4.42 использует Java 21 API. Пример переменных окружения:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21'
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
```

Скопируйте `local.properties.example` в `local.properties` и задайте
`MAPKIT_API_KEY`. `build.ps1` запускает Gradle Wrapper, собирает MapKit Full
только для `arm64-v8a` и подписывает APK прежним локальным отладочным ключом.

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
| `src/.../InstrumentMapSurfaceView.java` | MapKit и внешний Surface приборки |
| `apktool-src/` | манифест, ресурсы и встроенные данные APK |
| `app/build.gradle` | Gradle-варианты с HUD и без HUD, MapKit Full |
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
   Также никогда не добавляйте `local.properties` с ключом MapKit.
2. Выполните `test.ps1` и `build.ps1`.
3. Проверьте версию в `app/build.gradle`.
4. Для релиза приложите APK и SHA-256 из `Get-FileHash -Algorithm SHA256`.
