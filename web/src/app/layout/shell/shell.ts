import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  imports: [MatButtonModule, MatMenuModule, MatSidenavModule, MatToolbarModule, RouterLink, RouterOutlet],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly nome = computed(() => this.auth.usuario()?.nome ?? '');

  sair(): void {
    this.auth.sair().subscribe(() => void this.router.navigateByUrl('/entrar'));
  }
}
