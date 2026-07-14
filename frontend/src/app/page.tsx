"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { useMe } from "@/lib/auth/session";

/** La raíz no tiene contenido propio: manda a cada quien a donde le corresponde. */
export default function Home() {
  const { data: me, isPending, isError } = useMe();
  const router = useRouter();

  useEffect(() => {
    if (isError) {
      router.replace("/login");
    } else if (me) {
      router.replace(HOME_BY_ROLE[me.role]);
    }
  }, [me, isError, router]);

  return (
    <main className="flex-1 grid place-items-center">
      <p className="text-sm text-text-muted">{isPending ? "Cargando…" : ""}</p>
    </main>
  );
}
