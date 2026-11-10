package com.flux.categorias.interno;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** Todas as categorias do usuário (no máximo 200): as regras de árvore e de nome são conferidas em memória. */
    List<Categoria> findAllByUsuarioId(Long usuarioId);

    /** Igual ao das contas, com outra chave: serializa as alterações de categorias do usuário. */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended('categorias:' || :usuarioId, 0))",
            nativeQuery = true)
    Integer travarCategoriasDoUsuario(@Param("usuarioId") Long usuarioId);
}
