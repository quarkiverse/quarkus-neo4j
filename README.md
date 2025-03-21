# Quarkus Neo4j
<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-9-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.neo4j/quarkus-neo4j?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.neo4j/quarkus-neo4j)

## Introduction

Quarkus Neo4j is a Quarkus extension to connect to the [Neo4j graph database](https://neo4j.com).

It enables the use of the [Neo4j Java Driver](https://github.com/neo4j/neo4j-java-driver) in both JVM mode and native executables. It provide configuration properties to configure all relevant aspects of the driver.

## Documentation

The documentation for this extension can be found [here](https://quarkiverse.github.io/quarkiverse-docs/quarkus-neo4j/dev/index.html) while the documentation for the Neo4j Java Driver itself is in the official [manual](https://neo4j.com/docs/java-manual/4.4/).

Other extension that build on this extension and are known to work well with it:

* [Neo4j-Migrations](https://michael-simons.github.io/neo4j-migrations/current/#download_quarkus), an extension that allows running Cypher scripts in a controlled manner for migrating or refactoring databases
* [Neo4j-OGM for Quarkus](https://github.com/michael-simons/neo4j-ogm-quarkus), an extension bringing in Neo4j-OGM and a couple of shims allowing to use Neo4js object mapper without too much hassle inside Quarkus

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="http://michael-simons.eu"><img src="https://avatars.githubusercontent.com/u/526383?v=4?s=100" width="100px;" alt="Michael Simons"/><br /><sub><b>Michael Simons</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-neo4j/commits?author=michael-simons" title="Code">💻</a> <a href="#maintenance-michael-simons" title="Maintenance">🚧</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://lesincroyableslivres.fr/"><img src="https://avatars.githubusercontent.com/u/1279749?v=4?s=100" width="100px;" alt="Guillaume Smet"/><br /><sub><b>Guillaume Smet</b></sub></a><br /><a href="#maintenance-gsmet" title="Maintenance">🚧</a> <a href="https://github.com/quarkiverse/quarkus-neo4j/commits?author=gsmet" title="Documentation">📖</a></td>
      <td align="center" valign="top" width="14.28%"><a href="http://www.phillip-kruger.com"><img src="https://avatars.githubusercontent.com/u/6836179?v=4?s=100" width="100px;" alt="Phillip Krüger"/><br /><sub><b>Phillip Krüger</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-neo4j/commits?author=phillip-kruger" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/cescoffier"><img src="https://avatars.githubusercontent.com/u/402301?v=4?s=100" width="100px;" alt="Clement Escoffier"/><br /><sub><b>Clement Escoffier</b></sub></a><br /><a href="#question-cescoffier" title="Answering Questions">💬</a></td>
      <td align="center" valign="top" width="14.28%"><a href="http://gastaldi.wordpress.com"><img src="https://avatars.githubusercontent.com/u/54133?v=4?s=100" width="100px;" alt="George Gastaldi"/><br /><sub><b>George Gastaldi</b></sub></a><br /><a href="#infra-gastaldi" title="Infrastructure (Hosting, Build-Tools, etc)">🚇</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://thejavaguy.org/"><img src="https://avatars.githubusercontent.com/u/11942401?v=4?s=100" width="100px;" alt="Ivan Milosavljević"/><br /><sub><b>Ivan Milosavljević</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-neo4j/commits?author=TheJavaGuy" title="Documentation">📖</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/jmartisk"><img src="https://avatars.githubusercontent.com/u/937315?v=4?s=100" width="100px;" alt="Jan Martiska"/><br /><sub><b>Jan Martiska</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-neo4j/commits?author=jmartisk" title="Code">💻</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/nijuichien"><img src="https://avatars.githubusercontent.com/u/87717636?v=4?s=100" width="100px;" alt="Jui-Chien Ni"/><br /><sub><b>Jui-Chien Ni</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-neo4j/issues?q=author%3Anijuichien" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/injectives"><img src="https://avatars.githubusercontent.com/u/11927660?v=4?s=100" width="100px;" alt="Dmitriy Tverdiakov"/><br /><sub><b>Dmitriy Tverdiakov</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-neo4j/commits?author=injectives" title="Code">💻</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!
