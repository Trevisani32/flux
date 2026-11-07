import { FormControl, FormGroup } from '@angular/forms';
import { describe, expect, it } from 'vitest';

import { senhasIguais, textoObrigatorio } from './validadores';

describe('validadores', () => {
  it('textoObrigatorio recusa texto vazio ou só com espaços', () => {
    expect(textoObrigatorio(new FormControl('   '))).toEqual({ required: true });
    expect(textoObrigatorio(new FormControl(''))).toEqual({ required: true });
  });

  it('textoObrigatorio aceita texto preenchido', () => {
    expect(textoObrigatorio(new FormControl('a'))).toBeNull();
  });

  it('senhasIguais marca e depois desmarca a confirmação diferente', () => {
    const formulario = new FormGroup({ novaSenha: new FormControl('uma'), confirmarNovaSenha: new FormControl('outra') }, { validators: senhasIguais });
    expect(formulario.controls.confirmarNovaSenha.hasError('senhasDiferentes')).toBe(true);

    formulario.controls.confirmarNovaSenha.setValue('uma');
    expect(formulario.controls.confirmarNovaSenha.hasError('senhasDiferentes')).toBe(false);
  });
});
