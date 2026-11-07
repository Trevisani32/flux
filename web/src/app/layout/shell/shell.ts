import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map, tap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { Icone } from '../../shared/icone/icone';
import { Marca } from '../../shared/marca/marca';
import { QUEBRA_DESKTOP } from '../quebras';
import { BotaoUsuario } from './botao-usuario/botao-usuario';
import { Navegacao } from './navegacao/navegacao';

@Component({
  selector: 'app-shell',
  imports: [
    MatSidenavModule, MatMenuModule, MatDividerModule, RouterLink, RouterOutlet,
    Navegacao, BotaoUsuario, Marca, Icone,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Precisa vir antes de `desktop`: o tap abaixo roda já na assinatura.
  readonly gavetaAberta = signal(false);
  readonly desktop = toSignal(
    inject(BreakpointObserver).observe(QUEBRA_DESKTOP).pipe(
      map((estado) => estado.matches),
      tap(() => this.gavetaAberta.set(false)),
    ),
    { requireSync: true },
  );
  readonly nome = computed(() => this.auth.usuario()?.nome ?? '');
  readonly email = computed(() => this.auth.usuario()?.email ?? '');

  constructor() {
    this.router.events
      .pipe(filter((evento) => evento instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.gavetaAberta.set(false));
  }

  abrirGaveta(): void {
    this.gavetaAberta.set(true);
  }

  fecharGaveta(): void {
    this.gavetaAberta.set(false);
  }

  aoMudarGaveta(aberta: boolean): void {
    if (!this.desktop()) {
      this.gavetaAberta.set(aberta);
    }
  }

  sair(): void {
    this.auth.sair().subscribe(() => void this.router.navigateByUrl('/entrar'));
  }
}
