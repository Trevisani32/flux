import { Component, computed, inject } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-inicio',
  templateUrl: './inicio.html',
})
export class Inicio {
  private readonly auth = inject(AuthService);
  readonly nome = computed(() => this.auth.usuario()?.nome ?? '');
}
