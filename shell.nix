with (import <nixpkgs> {});

mkShell {
  buildInputs = [
    jdk17_headless
    redis
    sbt
  ];
  shellHook = ''
  '';
}
