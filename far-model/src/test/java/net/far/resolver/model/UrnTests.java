package net.far.resolver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UrnTests {

  @Test
  void shouldParseValidUrn() {
    final var urn = Urn.parse("urn:far:verra:VCS-1234");

    assertThat(urn.namespace()).isEqualTo("verra");
    assertThat(urn.identifier()).isEqualTo("VCS-1234");
  }

  @Test
  void shouldParseVerraNamespace() {
    final var urn = Urn.parse("urn:far:verra:VCS-981");

    assertThat(urn.namespace()).isEqualTo("verra");
    assertThat(urn.identifier()).isEqualTo("VCS-981");
  }

  @Test
  void shouldParseMiqNamespace() {
    final var urn = Urn.parse("urn:far:miq:CERT-5678");

    assertThat(urn.namespace()).isEqualTo("miq");
    assertThat(urn.identifier()).isEqualTo("CERT-5678");
  }

  @Test
  void shouldParseRecNamespace() {
    final var urn = Urn.parse("urn:far:rec:REC-0001");

    assertThat(urn.namespace()).isEqualTo("rec");
    assertThat(urn.identifier()).isEqualTo("REC-0001");
  }

  @Test
  void shouldParseNgcNamespace() {
    final var urn = Urn.parse("urn:far:ngc:NGC-42");

    assertThat(urn.namespace()).isEqualTo("ngc");
    assertThat(urn.identifier()).isEqualTo("NGC-42");
  }

  @Test
  void shouldParseCarbonNamespace() {
    final var urn = Urn.parse("urn:far:carbon:OFF-100");

    assertThat(urn.namespace()).isEqualTo("carbon");
    assertThat(urn.identifier()).isEqualTo("OFF-100");
  }

  @Test
  void shouldParseNamespaceWithHyphens() {
    final var urn = Urn.parse("urn:far:my-registry:ID-1");

    assertThat(urn.namespace()).isEqualTo("my-registry");
    assertThat(urn.identifier()).isEqualTo("ID-1");
  }

  @Test
  void shouldParseNamespaceWithDigits() {
    final var urn = Urn.parse("urn:far:reg2:ID-1");

    assertThat(urn.namespace()).isEqualTo("reg2");
    assertThat(urn.identifier()).isEqualTo("ID-1");
  }

  @Test
  void shouldRejectNullUrn() {
    assertThatThrownBy(() -> Urn.parse(null))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Invalid URN");
  }

  @Test
  void shouldRejectEmptyUrn() {
    assertThatThrownBy(() -> Urn.parse(""))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Invalid URN");
  }

  @Test
  void shouldRejectMissingPrefix() {
    assertThatThrownBy(() -> Urn.parse("verra:VCS-1234"))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Invalid URN");
  }

  @Test
  void shouldRejectWrongPrefix() {
    assertThatThrownBy(() -> Urn.parse("urn:other:verra:VCS-1234"))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Invalid URN");
  }

  @Test
  void shouldRejectMissingNamespace() {
    assertThatThrownBy(() -> Urn.parse("urn:far::VCS-1234"))
        .isInstanceOf(InvalidUrnException.class);
  }

  @Test
  void shouldRejectMissingIdentifier() {
    assertThatThrownBy(() -> Urn.parse("urn:far:verra:")).isInstanceOf(InvalidUrnException.class);
  }

  @Test
  void shouldRejectUrnWithOnlyPrefix() {
    assertThatThrownBy(() -> Urn.parse("urn:far:")).isInstanceOf(InvalidUrnException.class);
  }

  @Test
  void shouldRejectUrnWithNoColonAfterNamespace() {
    assertThatThrownBy(() -> Urn.parse("urn:far:verra")).isInstanceOf(InvalidUrnException.class);
  }

  @Test
  void shouldRejectNamespaceStartingWithDigit() {
    assertThatThrownBy(() -> new Urn("2bad", "ID-1"))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Invalid namespace");
  }

  @Test
  void shouldRejectNamespaceWithUppercase() {
    assertThatThrownBy(() -> new Urn("Verra", "ID-1"))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Invalid namespace");
  }

  @Test
  void shouldRejectNamespaceWithUnderscore() {
    assertThatThrownBy(() -> new Urn("my_reg", "ID-1"))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Invalid namespace");
  }

  @Test
  void shouldRejectBlankNamespace() {
    assertThatThrownBy(() -> new Urn("", "ID-1"))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Namespace must not be blank");
  }

  @Test
  void shouldRejectNullNamespace() {
    assertThatThrownBy(() -> new Urn(null, "ID-1"))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Namespace must not be blank");
  }

  @Test
  void shouldRejectBlankIdentifier() {
    assertThatThrownBy(() -> new Urn("verra", ""))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Identifier must not be blank");
  }

  @Test
  void shouldRejectNullIdentifier() {
    assertThatThrownBy(() -> new Urn("verra", null))
        .isInstanceOf(InvalidUrnException.class)
        .hasMessageContaining("Identifier must not be blank");
  }

  @Test
  void shouldProduceCorrectToString() {
    final var urn = new Urn("verra", "VCS-1234");

    assertThat(urn.toString()).isEqualTo("urn:far:verra:VCS-1234");
  }

  @Test
  void shouldRoundtripThroughParseAndToString() {
    final var original = "urn:far:verra:VCS-1234";
    final var urn = Urn.parse(original);

    assertThat(urn.toString()).isEqualTo(original);
  }

  @Test
  void shouldBeEqualWhenParsedFromSameString() {
    final var first = Urn.parse("urn:far:verra:VCS-1234");
    final var second = Urn.parse("urn:far:verra:VCS-1234");

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void shouldNotBeEqualWhenDifferentNamespace() {
    final var first = Urn.parse("urn:far:verra:VCS-1234");
    final var second = Urn.parse("urn:far:miq:VCS-1234");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void shouldNotBeEqualWhenDifferentIdentifier() {
    final var first = Urn.parse("urn:far:verra:VCS-1234");
    final var second = Urn.parse("urn:far:verra:VCS-5678");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void shouldHaveCorrectConstants() {
    assertThat(Urn.SCHEME).isEqualTo("urn");
    assertThat(Urn.NID).isEqualTo("far");
    assertThat(Urn.PREFIX).isEqualTo("urn:far:");
  }

  @Test
  void shouldHandleIdentifierWithColons() {
    final var urn = Urn.parse("urn:far:verra:VCS:1234:sub");

    assertThat(urn.namespace()).isEqualTo("verra");
    assertThat(urn.identifier()).isEqualTo("VCS:1234:sub");
  }
}
