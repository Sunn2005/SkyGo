import { Image } from "expo-image";

const paths = {
  chat: '<path d="M5 4h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-8l-6 4v-4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z"/><path d="m12 7 1.2 2.8L16 11l-2.8 1.2L12 15l-1.2-2.8L8 11l2.8-1.2Z"/>',
  send: '<path d="m3 3 18 9-18 9 4-9-4-9ZM7 12h14"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  plane:
    '<path transform="rotate(35 12 12)" d="M12 2c-1 0-1.5 1-1.5 2v5L3 13v2l7.5-2v5L8 20v2l4-1 4 1v-2l-2.5-2v-5l7.5 2v-2l-7.5-4V4c0-1-.5-2-1.5-2Z"/>',
  search: '<circle cx="10.8" cy="10.8" r="7.3"/><path d="m16.2 16.2 5 5"/>',
  arrow: '<path d="M3 12h18m-7-7 7 7-7 7"/>',
  trend: '<path d="m3 17 7-7 4 4 7-9m-7 0h7v7"/>',
  home: '<path d="m3 10 9-8 9 8M5 9v12h14V9M10 21v-8h4v8"/>',
  ticket:
    '<path d="M3 5h18v5a2 2 0 0 0 0 4v5H3v-5a2 2 0 0 0 0-4V5Z"/><path d="M15 5v2m0 4v2m0 4v2"/>',
  calendar:
    '<rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 3v4m8-4v4M4 10h16"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 3"/>',
  user: '<circle cx="12" cy="7" r="4"/><path d="M5 22v-2a7 7 0 0 1 14 0v2H5Z"/>',
} as const;

export function HomeIcon({
  name,
  color,
  size = 22,
}: {
  name: keyof typeof paths;
  color: string;
  size?: number;
}) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${paths[name]}</svg>`;
  return (
    <Image
      source={{ uri: `data:image/svg+xml;utf8,${encodeURIComponent(svg)}` }}
      style={{ width: size, height: size }}
      accessibilityElementsHidden
      importantForAccessibility="no"
    />
  );
}
