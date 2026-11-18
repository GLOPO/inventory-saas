package com.glory.inventorysaas.utils;

public final class DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private DistanceCalculator() {
        // Prevent instantiation
    }

    public static double calculateDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        double lat1 = Math.toRadians(latitude1);
        double lon1 = Math.toRadians(longitude1);
        double lat2 = Math.toRadians(latitude2);
        double lon2 = Math.toRadians(longitude2);

        double deltaLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.pow(Math.sin(deltaLon / 2), 2);

        // Protect against floating-point rounding errors
        a = Math.max(0.0, Math.min(1.0, a));

        double c = 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );

        return EARTH_RADIUS_KM * c;
    }

    public static void main(String[] args) {

        // Test 1: Distance between (0, 0) and (1, 0)
        double distance1 = calculateDistance(0, 0, 1, 0);

        // Test 2: Distance between the same point
        double distance2 = calculateDistance(0, 0, 0, 0);

        System.out.println("Test 1: " + distance1 + " km");
        System.out.println("Test 2: " + distance2 + " km");

        System.out.println("Test 1 passed: "
                + (Math.abs(distance1 - 111.19) < 0.01));

        System.out.println("Test 2 passed: "
                + (distance2 == 0.0));
    }
}
