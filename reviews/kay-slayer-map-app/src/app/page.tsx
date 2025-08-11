import Image from "next/image";

export default function Home() {
  return (
    <main className="p-6 space-y-4">
      <h1 className="text-3xl font-bold">KAY SLAYER MAPS</h1>
      <p className="text-gray-500">MVP con Next.js + Tailwind. Usa /maps para visualizar Google Maps.</p>
      <a href="/maps" className="text-blue-500 underline">Ir al mapa</a>
    </main>
  );
}
