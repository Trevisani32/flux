export interface Usuario {
  id: number;
  nome: string;
  email: string;
}

export interface SessaoResponse {
  tokenAcesso: string;
  expiraEm: string;
  usuario: Usuario;
}
