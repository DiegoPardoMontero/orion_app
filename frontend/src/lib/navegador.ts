/**
 * Los navegadores de dentro de las apps. Google devuelve «403 disallowed_useragent» en ellos, y los
 * enlaces de la portada viven justamente en las biografías de Instagram y TikTok.
 */
export function esNavegadorDeApp(ua: string): boolean {
  return /Instagram|FBAN|FBAV|FB_IAB|musical_ly|TikTok|BytedanceWebview|Line\/|Snapchat|; wv\)/i.test(ua);
}
