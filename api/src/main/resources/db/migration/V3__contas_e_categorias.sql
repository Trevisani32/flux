CREATE TABLE contas (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id          BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    nome                VARCHAR(60)   NOT NULL,
    nome_chave          VARCHAR(240)  NOT NULL,
    tipo                VARCHAR(20)   NOT NULL,
    saldo_inicial       NUMERIC(19,2) NOT NULL,
    data_saldo_inicial  DATE          NOT NULL,
    cor                 VARCHAR(20),
    arquivada_em        TIMESTAMPTZ,
    criada_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizada_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_contas_tipo
        CHECK (tipo IN ('CORRENTE', 'POUPANCA', 'CARTEIRA', 'INVESTIMENTO')),
    CONSTRAINT ck_contas_cor
        CHECK (cor IS NULL OR cor IN ('OURO', 'LARANJA', 'ROSA', 'ROXO', 'INDIGO', 'AZUL', 'CIANO', 'CINZA'))
);

CREATE INDEX ix_contas_usuario ON contas (usuario_id);
CREATE UNIQUE INDEX ux_contas_nome_ativa ON contas (usuario_id, nome_chave) WHERE arquivada_em IS NULL;

CREATE TABLE categorias (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id           BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    natureza             VARCHAR(10)   NOT NULL,
    categoria_mae_id     BIGINT,
    nome                 VARCHAR(60)   NOT NULL,
    nome_chave           VARCHAR(240)  NOT NULL,
    arquivada_em         TIMESTAMPTZ,
    arquivada_com_a_mae  BOOLEAN       NOT NULL DEFAULT false,
    criada_em            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizada_em        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_categorias_natureza CHECK (natureza IN ('RECEITA', 'DESPESA')),
    CONSTRAINT ck_categorias_mae_diferente CHECK (categoria_mae_id IS NULL OR categoria_mae_id <> id),
    CONSTRAINT ck_categorias_arquivada_com_a_mae CHECK (NOT arquivada_com_a_mae OR arquivada_em IS NOT NULL),
    CONSTRAINT uq_categorias_id_usuario_natureza UNIQUE (id, usuario_id, natureza),
    -- A subcategoria aponta para uma categoria do mesmo usuário e da mesma natureza.
    CONSTRAINT fk_categorias_mae FOREIGN KEY (categoria_mae_id, usuario_id, natureza)
        REFERENCES categorias (id, usuario_id, natureza) ON DELETE CASCADE
);

CREATE INDEX ix_categorias_usuario ON categorias (usuario_id, natureza);
CREATE INDEX ix_categorias_mae ON categorias (categoria_mae_id);
CREATE UNIQUE INDEX ux_categorias_nome_ativa
    ON categorias (usuario_id, natureza, categoria_mae_id, nome_chave) NULLS NOT DISTINCT
    WHERE arquivada_em IS NULL;

-- Categorias padrão para os usuários que já existem. Quem for criado depois recebe as
-- mesmas pelo evento UsuarioCriado (CategoriasPadrao.LISTA). O CategoriasPadraoNaMigracaoIT
-- confere que as duas listas são iguais.
INSERT INTO categorias (usuario_id, natureza, nome, nome_chave)
SELECT u.id, p.natureza, p.nome, p.nome_chave
FROM usuarios u
CROSS JOIN (VALUES
    ('DESPESA', 'Moradia', 'moradia'),
    ('DESPESA', 'Alimentação', 'alimentacao'),
    ('DESPESA', 'Transporte', 'transporte'),
    ('DESPESA', 'Saúde', 'saude'),
    ('DESPESA', 'Educação', 'educacao'),
    ('DESPESA', 'Lazer', 'lazer'),
    ('DESPESA', 'Assinaturas', 'assinaturas'),
    ('DESPESA', 'Compras', 'compras'),
    ('DESPESA', 'Impostos e taxas', 'impostos e taxas'),
    ('DESPESA', 'Outras despesas', 'outras despesas'),
    ('RECEITA', 'Salário', 'salario'),
    ('RECEITA', 'Freelance', 'freelance'),
    ('RECEITA', 'Rendimentos', 'rendimentos'),
    ('RECEITA', 'Reembolsos', 'reembolsos'),
    ('RECEITA', 'Outras receitas', 'outras receitas')
) AS p (natureza, nome, nome_chave);

INSERT INTO categorias (usuario_id, natureza, categoria_mae_id, nome, nome_chave)
SELECT mae.usuario_id, mae.natureza, mae.id, s.nome, s.nome_chave
FROM categorias mae
JOIN (VALUES
    ('Moradia', 'Aluguel', 'aluguel'),
    ('Moradia', 'Condomínio', 'condominio'),
    ('Moradia', 'Contas da casa', 'contas da casa'),
    ('Alimentação', 'Mercado', 'mercado'),
    ('Alimentação', 'Restaurante', 'restaurante'),
    ('Alimentação', 'Delivery', 'delivery'),
    ('Transporte', 'Combustível', 'combustivel'),
    ('Transporte', 'App de transporte', 'app de transporte'),
    ('Transporte', 'Transporte público', 'transporte publico')
) AS s (mae, nome, nome_chave)
    ON s.mae = mae.nome AND mae.natureza = 'DESPESA' AND mae.categoria_mae_id IS NULL;
