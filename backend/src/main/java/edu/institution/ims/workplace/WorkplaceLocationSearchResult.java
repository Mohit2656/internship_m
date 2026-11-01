package edu.institution.ims.workplace;

public record WorkplaceLocationSearchResult(String name, String address, double latitude, double longitude,
        WorkplaceLocationMatchQuality matchQuality) {
    public WorkplaceLocationSearchResult(String name, String address, double latitude, double longitude) {
        this(name, address, latitude, longitude, WorkplaceLocationMatchQuality.APPROXIMATE_ADDRESS);
    }
}
