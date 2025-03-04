Please go through the [[JMC Overview#Architecture]] before reading the development guide. 
## Style guidelines

We will be using the Google AOSP standard for formatting. The gradle build uses CheckStyle - the rules of which reside in `config/checkstyle/checkstyle.xml`
## IDE Setup

We recommend using the IntelliJ IDE with the following plugins to ensure smooth development.

- CheckStyle-IDEA and configure to use the version `10.19.0`
- google-java-format version `1.24.0.0` (set the style to AOSP style)

The formatting guidelines follows the Google Java Style guide (AOSP style with 4 indents) and the pipelines/hooks are configured to run CheckStyle. The CheckStyle configuration resides in `config/checkstyle/checkstyle.yml`
## Issues

Refer to [[Backlog]] for existing tasks. Workflow,
1. Create a feature branch
2. Code solution
3. Test
4. Merge `main` and test again
5. Raise Merge request

(PS: These notes are compatible with Obsidian. Open the `docs` folder as vault to use all the features)