"use client";

import { use } from "react";
import { HiloSoporte } from "@/components/HiloSoporte";

export default function HiloAyudaPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = use(params);
  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-8 lg:px-8">
      <HiloSoporte code={code} />
    </main>
  );
}
