package com.example.instrumentawdprobe;

final class SpeedCamera {
    final int id;
    final double longitude;
    final double latitude;
    final int type;
    final int speed;
    final int directionType;
    final int direction;
    final int alertDistanceMeters;
    final int angleToleranceDegrees;

    SpeedCamera(int id, double longitude, double latitude, int type,
                int speed, int directionType, int direction) {
        this(id, longitude, latitude, type, speed, directionType, direction, 0, 0);
    }

    SpeedCamera(int id, double longitude, double latitude, int type,
                int speed, int directionType, int direction,
                int alertDistanceMeters, int angleToleranceDegrees) {
        this.id = id;
        this.longitude = longitude;
        this.latitude = latitude;
        this.type = type;
        this.speed = speed;
        this.directionType = directionType;
        this.direction = direction;
        this.alertDistanceMeters = Math.max(0, alertDistanceMeters);
        this.angleToleranceDegrees = Math.max(0, angleToleranceDegrees);
    }

    int effectiveWarningDistance(int userDistanceMeters) {
        // The source DISTANCE value is its producer's preferred warning point,
        // not a geographic validity boundary.  The application owns the
        // speed-dependent approach distance, so do not cap it here.
        return userDistanceMeters;
    }

    float effectiveAngleTolerance() {
        if (angleToleranceDegrees <= 0) return 28f;
        return Math.max(8f, Math.min(35f, angleToleranceDegrees + 3f));
    }

    String typeLabel() {
        switch (type) {
            case 1: return "КАМЕРА";
            case 2: return "КАМЕРА · СВЕТОФОР";
            case 3: return "КОНТРОЛЬ СВЕТОФОРА";
            case 4: return "СРЕДНЯЯ СКОРОСТЬ";
            case 5: return "МОБИЛЬНАЯ КАМЕРА";
            case 6: return "Ж/Д ПЕРЕЕЗД";
            case 7: return "ВОЗМОЖНЫЙ КОНТРОЛЬ";
            default: return "ДОРОЖНЫЙ КОНТРОЛЬ";
        }
    }
}
