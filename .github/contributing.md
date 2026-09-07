# Contributing

Feel free to fork and open a PR, or create a branch within the repo and PR into `main`.

The `main` branch is protected against direct pushes - all changes should go through a PR.

## Multi-Version / Multi-Loader Development

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build for multiple
Minecraft versions and loaders (Fabric + Forge) from a single codebase. Version-specific
code uses Stonecutter's conditional syntax:

```java
//? if >= 1.21.9
useNewApi();
//? if < 1.21.9
/*useOldApi();*/
```

All versions are built from the `main` branch - there are no separate version branches.

## Building

```bash
./gradlew build
```

This compiles and tests every active loader variant and produces the loader jars under
`fabric/versions/*/build/libs/` and `forge/versions/*/build/libs/`.

## Testing

```bash
./gradlew test        # unit tests
./gradlew smokeTest   # smoke test suite
```

## CI/CD

- **Build** (`build.yml`): compiles and tests on every push and pull request, and uploads
  the loader jars as build artifacts.
- **Smoke** (`smoke.yml`): runs the `smokeTest` suite on every push and pull request.
- **CodeQL** (`codeql.yml`): security + quality code scanning.
- **Publish** (`publish.yml`): builds and (when configured) publishes to Modrinth and/or
  CurseForge on a published GitHub Release. It is **dormant until configured** - see the
  header comment in `publish.yml` for the secrets and variables to set.
- **Publish Existing Release** (`publish-existing-release.yml`): re-publishes an already
  built release's jars without rebuilding (manual, admin-only).

## License

See [LICENSE](../LICENSE). When reusing the code, please reference this repository and its
authors.
