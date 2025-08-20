package reveila.spring;

/**
 * A Data Transfer Object for string content
 */
public class StringContentDTO {
    
    private String content;

    public StringContentDTO() {
    }

    public StringContentDTO(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}