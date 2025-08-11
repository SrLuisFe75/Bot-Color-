"use client";

import { GoogleMap, useJsApiLoader, Marker, Circle } from "@react-google-maps/api";
import { useCallback, useMemo, useState } from "react";

const containerStyle = {
  width: "100%",
  height: "70vh",
};

export default function MapsPage() {
  const apiKey = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY as string | undefined;

  const { isLoaded } = useJsApiLoader({
    id: "google-map-script",
    googleMapsApiKey: apiKey ?? "",
    libraries: ["geometry", "places", "visualization"],
  });

  const [center] = useState({ lat: 40.7128, lng: -74.006 });
  const [radius] = useState(5000);

  const onLoad = useCallback((map: google.maps.Map) => {
    // Map ready
  }, []);

  const onUnmount = useCallback((map: google.maps.Map) => {
    // Cleanup if needed
  }, []);

  const options = useMemo<google.maps.MapOptions>(
    () => ({
      mapTypeId: "roadmap",
      disableDefaultUI: false,
      zoomControl: true,
      fullscreenControl: true,
      streetViewControl: false,
      styles: [
        { elementType: "geometry", stylers: [{ color: "#1f2937" }] },
        { elementType: "labels.text.fill", stylers: [{ color: "#9ca3af" }] },
        { elementType: "labels.text.stroke", stylers: [{ color: "#111827" }] },
      ],
    }),
    []
  );

  if (!apiKey) {
    return (
      <div className="p-6">
        <h1 className="text-xl font-semibold">Falta la API key</h1>
        <p>Define NEXT_PUBLIC_GOOGLE_MAPS_API_KEY en .env.local</p>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-bold">Mapa (Google Maps)</h1>
      {isLoaded ? (
        <GoogleMap
          mapContainerStyle={containerStyle}
          center={center}
          zoom={13}
          onLoad={onLoad}
          onUnmount={onUnmount}
          options={options}
        >
          <Marker position={center} />
          <Circle center={center} radius={radius} options={{
            strokeColor: "#ef4444",
            strokeOpacity: 0.8,
            strokeWeight: 2,
            fillColor: "#ef4444",
            fillOpacity: 0.1,
          }} />
        </GoogleMap>
      ) : (
        <div>Cargando Google Maps...</div>
      )}
    </div>
  );
}