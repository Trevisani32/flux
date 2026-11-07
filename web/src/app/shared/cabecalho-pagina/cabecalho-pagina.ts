import { Component, input } from '@angular/core';

@Component({
  selector: 'app-cabecalho-pagina',
  templateUrl: './cabecalho-pagina.html',
  styleUrl: './cabecalho-pagina.scss',
})
export class CabecalhoPagina {
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string>();
}
