

🔥 Lista dos Verbos Mais Usados no Builder (com explicação)
✅ 1. withXxx(...)

Quando usar:
Para configurar propriedades simples, que normalmente são valores diretos (String, int, BigDecimal, enums, Value Objects).

Exemplos:

withName("Marcus")

withStatus(OrderStatus.DRAFT)

withDiscount(new BigDecimal("10.00"))

Regra de ouro:

Use quando a propriedade for atributo direto do aggregate/objeto.

✅ 2. addXxx(...)

Quando usar:
Para alimentar listas, coleções, agregados internos, especialmente quando o objeto contém vários itens.

Exemplos:

addItem(productId, qty, price)

addTag("urgent")

addRole(Role.ADMIN)

Regra de ouro:

Sempre que o atributo for um List<>, Set<>, ou coleção.

✅ 3. of(...)

Quando usar:
É um factory method dentro do builder.
Serve para converter tipos externos ou construir o builder a partir de algo diferente.

Exemplos:

of(existingDTO)

of(jsonNode)

of(command)

Regra de ouro:

Use quando você recebe dados de outra camada, especialmente DTO, JSON, banco, API, etc.

✅ 4. from(...)

Quando usar:
Para criar um builder clonado a partir de outro objeto já existente, possibilitando alterações imutáveis.

Exemplos:

builderFrom(order)

from(existingUser)

Regra de ouro:

Use quando precisa criar um novo objeto baseado em um já existente (immutability / copy-on-write).

✅ 5. withXxxCalculated()

Quando usar:
Quando o valor não é passado pelo cliente, mas é derivado de outros campos.

Exemplos:

withTaxCalculated()

withTotalCalculated()

Regra de ouro:

Quando a propriedade depende de cálculo interno.

✅ 6. withoutXxx()

Quando usar:
Para criar variações envolvendo remoção de atributos opcionais.

Exemplos:

withoutDiscount()

withoutTags()

Regra de ouro:

Útil para objetos com muitos campos opcionais.

✅ 7. enableXxx() / disableXxx()

Quando usar:
Para ativar/desativar flags booleanas.

Exemplos:

enableNotifications()

disableTracking()

Regra de ouro:

Quando você quer evitar withNotifications(true).

✅ 8. usingXxx(...)

Quando usar:
Quando injeta uma estratégia, algoritmo ou comportamento.

Exemplos:

usingPriceCalculator(calculator)

usingClock(clock)

Regra de ouro:

Usado quando o Builder precisa receber comportamentos, não dados.

✅ 9. viaXxx(...)

Quando usar:
Quando a construção do objeto depende de origem ou mecanismo externo.

Exemplos:

viaApi(apiResponse)

viaDatabase(resultSet)

Regra de ouro:

Parecido com of, mas deixa explícita a origem.

✅ 10. fromXxx(...)

Quando usar:
Versões específicas de from, relacionadas a tipos distintos.

Exemplos:

fromDTO(dto)

fromSnapshot(snapshot)

Regra de ouro:

Use quando possui várias origens possíveis e precisa ser explícito.

✅ 11. but()

Quando usar:
Representa uma cópia do builder atual, porém alterando algo.

Exemplo:

Order order2 = Order.builderFrom(order)
.but()
.withDiscount(BigDecimal.ZERO)
.build();


Regra de ouro:

Muito usado em testes, fluxo de negócio e imutabilidade.

✅ 12. reset()

Quando usar:
Quando o builder é reutilizado para criar vários objetos.

Regra de ouro:

Normalmente usado em Builders complexos ou stateful.

❗ O que não usar

setXxx → quebra o conceito de imutabilidade

getXxx dentro do builder → antipadrão

doXxx → usado para ação, não para construção

🎯 Lista final resumida
Verbo	Quando usar
withXxx	Atributos simples
addXxx	Coleções
of	Dados externos (DTO, JSON, API)
from	Copiar um objeto existente
withXxxCalculated	Propriedades derivadas
withoutXxx	Remover opcionais
enable/disableXxx	Flags booleanas
usingXxx	Injeção de estratégia
viaXxx	Origem explícita de dados
fromXxx	Origem específica (DTO, snapshot…)
but	Alterar algo mantendo o resto
reset

































