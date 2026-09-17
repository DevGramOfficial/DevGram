# DevGram Plugin SDK

The complete SDK and native runtime source is published separately at
`https://github.com/DevGramOfficial/DevGramSDK`. It contains the public `devgram.*`
modules, `.dgplugin` loader, Android bridge and local Dev Server implementation.

DevGram uses native `.dgplugin` packages. They are ZIP archives with this layout:

```text
my_plugin.dgplugin
|- manifest.json
|- main.py
|- locales/           # optional: en.json, ru.json
|- assets/            # optional plugin assets
|- wheels/            # optional bundled Python wheels
```

`manifest.json` uses `main` (normally `main.py`) for the entry point. A stable
`id` is required by the loader; `name`, `version`, `author`, `description` and
`icon` provide the installation and catalog metadata. The package is validated,
extracted to a private directory, and installed atomically. A failed update
restores the previous working archive.

The validator rejects unsafe or missing entry points, duplicate ZIP names,
path traversal, packages with more than 4096 entries, incompatible minimum
versions and requirements without a matching bundled wheel.

## Minimal plugin

```python
from devgram import BasePlugin
from devgram.ui import Header, Switch, Button
from devgram.ui.bulletin import BulletinHelper

class Example(BasePlugin):
    id = "example.plugin"
    name = "Example"
    version = "1.0.0"
    author = "Your name"

    def settings(self):
        return [
            Header(text="Example settings"),
            Switch(key="enabled", text="Enabled", default=True),
            Button(key="test", text="Show bulletin"),
        ]

    def on_setting_click(self, key):
        if key == "test":
            BulletinHelper.show_success("It works")

    def menu_items(self):
        return ["Copy message"]

    def on_menu_click(self, label, message_text, dialog_id):
        if label == "Copy message":
            self.copy(message_text)
            self.bulletin("Copied", kind="success")

    def on_update_hook(self, update_name, account, update):
        # Handle a raw Telegram update when needed.
        pass

plugin = Example()
```

## Runtime API

- `send_message`, `send_photo`, `send_file`, `send_formatted_text`;
- `edit_message`, `send_request`, `tl()` for native TL requests;
- `observe(notification_id, callback)` and `ObserverHandle.close()`;
- account-aware controllers through `devgram.client_utils`;
- `run_on_ui_thread`, `run_on_queue`, clipboard helpers and Java listeners;
- `settings()` with `Header`, `Switch`, `Input`, `Selector`, `Button` and `Text`;
- `register_pill()` / `unregister_pills()` for interactive Pill Stack widgets;
- `asset_path()` and localized `string()` resources;
- `hook()` and `java_class()` for explicitly requested native integrations.

Callbacks are isolated from the client. Exceptions are logged and do not stop
other plugins. Safe mode disables plugin callbacks and remote developer tools.

## Formatted text

Use `devgram.text_formatting` to create native Telegram entities:

```python
from devgram.client_utils import send_formatted_text
from devgram.text_formatting import bold, italic, text_url

text = "DevGram"
send_formatted_text(peer_id, text, [bold(0, 7)], account=account)
```

Entities are converted to Telegram's real `TLRPC.MessageEntity` objects before
sending, rather than being rendered as markup text.

## Development

### DevGramBuilder

DevGramBuilder is the official CLI for creating, validating and packaging
native DevGram `.dgplugin` archives. Install it from its dedicated repository:

```bash
python3 -m pip install --upgrade "git+https://github.com/DevGramOfficial/DevGramBuilder.git"
dgb --version
```

When working from a local DevGramBuilder checkout, use
`python3 -m pip install -e .` instead.

On Windows, use `py -3` instead of `python3` when needed.

Create and build a project:

```bash
mkdir my-plugin
cd my-plugin
dgb new
dgb build --ast --verbose --no-folder
```

The same build command has a compact short form:

```bash
dgb build -a -v -nf
```

The archive is written to `builds/<id>-<version>.dgplugin`. A compiled build
requires Python 3.11 because DevGram embeds Python 3.11:

```bash
dgb build --compile 2 --verbose --no-folder
# or: dgb build -c 2 -v -nf
```

Other supported commands include `dgb watch`, `dgb cached`, `dgb add-ignore`,
`dgb del-ignore`, `dgb stats` and `dgb upload`. Run `dgb --help` or
`dgb build --help` for the complete option list.

The complete Builder and `.dgplugin` documentation is published at
`https://docs.devgram.space/docs/builder`.

The generated project has this layout:

```text
my-plugin/
|- devgram-builder.json
|- .devgrambuilder/config.json
|- src/main.py
|- assets/
|- locales/
`- wheels/
```

`devgram-builder.json` is development metadata. During a build it is converted
to the package-level `manifest.json` expected by DevGram.

### Dev Server

Validate and upload a package from a connected debug build:

```bash
python3 tools/devgram_dev.py upload my_plugin.dgplugin
```

DevGramBuilder can build and upload in one command as well:

```bash
export DEVGRAM_TOKEN="token_from_app"
dgb upload
```

Enable DevGram developer mode first. The upload endpoint validates the archive,
uses the local developer-server token, and reloads the package without
restarting the app.
