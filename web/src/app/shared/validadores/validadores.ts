import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Como o Validators.required, mas recusa também texto só com espaços. */
export const textoObrigatorio: ValidatorFn = (controle: AbstractControl): ValidationErrors | null => {
  const valor: unknown = controle.value;
  return typeof valor === 'string' && valor.trim() !== '' ? null : { required: true };
};

/**
 * Validador de grupo: marca `senhasDiferentes` no campo `confirmarNovaSenha` quando
 * ele difere de `novaSenha`. Igual ao da troca de senha da Fatia 00.
 */
export function senhasIguais(controle: AbstractControl): ValidationErrors | null {
  const { novaSenha, confirmarNovaSenha } = controle.value as { novaSenha?: string; confirmarNovaSenha?: string };
  const confirmacao = controle.get('confirmarNovaSenha');
  if (novaSenha !== confirmarNovaSenha) {
    if (!confirmacao?.hasError('senhasDiferentes')) {
      confirmacao?.setErrors({ ...confirmacao.errors, senhasDiferentes: true });
    }
    return { senhasDiferentes: true };
  }
  if (confirmacao?.hasError('senhasDiferentes')) {
    const { senhasDiferentes, ...demaisErros } = confirmacao.errors ?? {};
    void senhasDiferentes;
    confirmacao.setErrors(Object.keys(demaisErros).length === 0 ? null : demaisErros);
  }
  return null;
}
