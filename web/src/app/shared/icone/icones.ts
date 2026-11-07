/** Traços dos ícones (viewBox 0 0 24 24), copiados do mockup da Fatia 01. */
export const ICONES = {
  inicio: ['M3 10.5 12 3l9 7.5', 'M5 9v11a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9'],
  lancamentos: ['M7 4 3 8l4 4', 'M3 8h13', 'm17 12 4 4-4 4', 'M21 16H8'],
  importar: ['M12 3v12', 'm7 10 5 5 5-5', 'M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4'],
  cartao: [
    'M5 5h14a2.5 2.5 0 0 1 2.5 2.5v9a2.5 2.5 0 0 1-2.5 2.5H5a2.5 2.5 0 0 1-2.5-2.5v-9A2.5 2.5 0 0 1 5 5z',
    'M2.5 10h19',
    'M6.5 15h4',
  ],
  carteira: [
    'M6 6h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z',
    'M3 10h18',
    'M16 15h2',
    'M6 6V5a2 2 0 0 1 2-2h8',
  ],
  menu: ['M4 7h16M4 12h16M4 17h10'],
  olho: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z', 'M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0'],
  pessoa: ['M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0', 'M4 21a8 8 0 0 1 16 0'],
  sair: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  'mais-h': [
    'M4 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0',
    'M11 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0',
    'M18 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0',
  ],
  baixo: ['m6 9 6 6 6-6'],
  codigo: ['m8 7-5 5 5 5', 'm16 7 5 5-5 5'],
} as const satisfies Record<string, readonly string[]>;

export type NomeIcone = keyof typeof ICONES;
