import { describe, expect, it } from 'vitest';

import { tokenDoFragmento } from './token-do-fragmento';

describe('tokenDoFragmento', () => {
  it('lê o token do fragmento', () => {
    expect(tokenDoFragmento('token=abc_DEF-123')).toBe('abc_DEF-123');
  });

  it('devolve null sem token válido', () => {
    expect(tokenDoFragmento(null)).toBeNull();
    expect(tokenDoFragmento('')).toBeNull();
    expect(tokenDoFragmento('token=')).toBeNull();
    expect(tokenDoFragmento('x=1')).toBeNull();
  });
});
