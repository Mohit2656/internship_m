"use client";

import { useEffect } from "react";
import { divIcon, type LeafletEvent, type Marker as LeafletMarker } from "leaflet";
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { validWorkplaceCoordinates, type WorkplaceCoordinates } from "@/lib/workplace-location";

type Props = { position: WorkplaceCoordinates | null; disabled: boolean; onSelect: (position: WorkplaceCoordinates) => void; onTileError: () => void };

// Static HTML avoids Leaflet's default external marker image paths in Next builds.
const workplacePin = divIcon({
  className: "workplace-osm-pin",
  html: '<div style="width:26px;height:26px;border:3px solid white;border-radius:50% 50% 50% 0;background:#2563eb;transform:rotate(-45deg);box-shadow:0 2px 5px #0006"></div>',
  iconSize: [26, 26], iconAnchor: [13, 26],
});

function Selection({ position, disabled, onSelect }: Pick<Props, "position" | "disabled" | "onSelect">) {
  const map = useMapEvents({ click(event) {
    if (disabled) return;
    const point = event.latlng.wrap();
    const coordinates = { latitude: point.lat, longitude: point.lng };
    if (validWorkplaceCoordinates(coordinates)) onSelect(coordinates);
  } });
  useEffect(() => {
    if (position) map.setView([position.latitude, position.longitude], Math.max(map.getZoom(), 16));
  }, [map, position?.latitude, position?.longitude]);
  return position ? <Marker position={[position.latitude, position.longitude]} icon={workplacePin} draggable={!disabled} title="Selected workplace entrance" eventHandlers={{ dragend(event: LeafletEvent) {
    if (disabled) return;
    const point = (event.target as LeafletMarker).getLatLng().wrap();
    const coordinates = { latitude: point.lat, longitude: point.lng };
    if (validWorkplaceCoordinates(coordinates)) onSelect(coordinates);
  } }} /> : null;
}

function MapSize() {
  const map = useMap();
  useEffect(() => {
    // Registration panels may become visible after the map has mounted.
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(() => map.invalidateSize());
    observer?.observe(map.getContainer());
    map.invalidateSize();
    return () => observer?.disconnect();
  }, [map]);
  return null;
}

export default function OpenStreetMapWorkplaceMap({ position, disabled, onSelect, onTileError }: Props) {
  const tileUrl = process.env.NEXT_PUBLIC_OSM_TILE_URL || "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
  const providerAttribution = process.env.NEXT_PUBLIC_OSM_TILE_PROVIDER_NAME;
  const providerUrl = process.env.NEXT_PUBLIC_OSM_TILE_PROVIDER_URL;
  return <div className="relative z-0 overflow-hidden rounded-lg border border-slate-200" role="group" aria-label="Interactive workplace map">
    <MapContainer center={position ? [position.latitude, position.longitude] : [20.5937, 78.9629]} zoom={position ? 16 : 4} maxZoom={19} minZoom={2} scrollWheelZoom={false} worldCopyJump style={{ height: 320, width: "100%" }}>
      <TileLayer url={tileUrl} attribution={'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'} maxZoom={19} keepBuffer={0} updateWhenIdle updateWhenZooming={false} referrerPolicy="strict-origin-when-cross-origin" eventHandlers={{ tileerror: onTileError }} />
      <Selection position={position} disabled={disabled} onSelect={onSelect} />
      <MapSize />
    </MapContainer>
    {providerAttribution && <p className="bg-white px-2 py-1 text-right text-xs text-slate-600">Tiles: {providerUrl?.startsWith("https://") ? <a href={providerUrl} target="_blank" rel="noopener noreferrer" className="underline">{providerAttribution}</a> : providerAttribution}</p>}
  </div>;
}
